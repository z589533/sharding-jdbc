/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.executor;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.exception.ShardingJdbcException;
import com.dangdang.ddframe.rdb.sharding.executor.event.AbstractExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DMLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DQLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.EventExecutionType;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorDataMap;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorExceptionHandler;
import com.dangdang.ddframe.rdb.sharding.executor.type.batch.BatchPreparedStatementUnit;
import com.dangdang.ddframe.rdb.sharding.executor.type.prepared.PreparedStatementUnit;
import com.dangdang.ddframe.rdb.sharding.executor.type.statement.StatementUnit;
import com.dangdang.ddframe.rdb.sharding.util.EventBusInstance;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * SQL execute engine.
 *
 * @author gaohongtao
 * @author zhangliang
 */
@Slf4j
public final class ExecutorEngine implements AutoCloseable {

    private final ListeningExecutorService executorService;

    public ExecutorEngine(final int executorSize) {
//        executorService = MoreExecutors.listeningDecorator(new ThreadPoolExecutor(
//                executorSize, executorSize, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactoryBuilder().setDaemon(true).setNameFormat("ShardingJDBC-%d").build()));

        executorService = MoreExecutors.listeningDecorator(new ForkJoinPool
                (executorSize,
                        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                        null, true));

        MoreExecutors.addDelayedShutdownHook(executorService, 60, TimeUnit.SECONDS);
    }

    /**
     * Execute statement.
     *
     * @param sqlType         SQL type
     * @param statementUnits  statement execute unit
     * @param executeCallback statement execute callback
     * @param <T>             class type of return value
     * @return execute result
     */
    public <T> List<T> executeStatement(final SQLType sqlType, final Collection<StatementUnit> statementUnits, final ExecuteCallback<T> executeCallback) {
        return execute(sqlType, statementUnits, Collections.<List<Object>>emptyList(), executeCallback);
    }

    /**
     * Execute prepared statement.
     *
     * @param sqlType                SQL type
     * @param preparedStatementUnits prepared statement execute unit
     * @param parameters             parameters for SQL placeholder
     * @param executeCallback        prepared statement execute callback
     * @param <T>                    class type of return value
     * @return execute result
     */
    public <T> List<T> executePreparedStatement(
            final SQLType sqlType, final Collection<PreparedStatementUnit> preparedStatementUnits, final List<Object> parameters, final ExecuteCallback<T> executeCallback) {
        return execute(sqlType, preparedStatementUnits, Collections.singletonList(parameters), executeCallback);
    }

    /**
     * Execute add batch.
     *
     * @param sqlType                     SQL type
     * @param batchPreparedStatementUnits prepared statement execute unit for batch
     * @param parameterSets               parameters for SQL placeholder
     * @param executeCallback             prepared statement execute callback
     * @return execute result
     */
    public List<int[]> executeBatch(
            final SQLType sqlType, final Collection<BatchPreparedStatementUnit> batchPreparedStatementUnits, final List<List<Object>> parameterSets, final ExecuteCallback<int[]> executeCallback) {
        return execute(sqlType, batchPreparedStatementUnits, parameterSets, executeCallback);
    }

    private <T> List<T> execute(
            final SQLType sqlType, final Collection<? extends BaseStatementUnit> baseStatementUnits, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback) {
        if (baseStatementUnits.isEmpty()) {
            return Collections.emptyList();
        }
        Iterator<? extends BaseStatementUnit> iterator = baseStatementUnits.iterator();
        BaseStatementUnit firstInput = iterator.next();
        ListenableFuture<List<T>> restFutures = asyncExecute(sqlType, Lists.newArrayList(iterator), parameterSets, executeCallback);
        T firstOutput;
        List<T> restOutputs;
        try {
            firstOutput = syncExecute(sqlType, firstInput, parameterSets, executeCallback);
            restOutputs = restFutures.get();
            //CHECKSTYLE:OFF
        } catch (final Exception ex) {
            //CHECKSTYLE:ON
            ExecutorExceptionHandler.handleException(ex);
            return null;
        }
        List<T> result = Lists.newLinkedList(restOutputs);
        result.add(0, firstOutput);
        return result;
    }

    private <T> ListenableFuture<List<T>> asyncExecute(
            final SQLType sqlType, final Collection<BaseStatementUnit> baseStatementUnits, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback) {
        List<ListenableFuture<T>> result = new ArrayList<>(baseStatementUnits.size());
        final boolean isExceptionThrown = ExecutorExceptionHandler.isExceptionThrown();
        final Map<String, Object> dataMap = ExecutorDataMap.getDataMap();
        baseStatementUnits.stream().forEach(each -> {
            result.add(executorService.submit(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    return executeInternal(sqlType, each, parameterSets, executeCallback, isExceptionThrown, dataMap);
                }
            }));
        });
        return Futures.allAsList(result);
    }

    private <T> T syncExecute(final SQLType sqlType, final BaseStatementUnit baseStatementUnit, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback) throws Exception {
        return executeInternal(sqlType, baseStatementUnit, parameterSets, executeCallback, ExecutorExceptionHandler.isExceptionThrown(), ExecutorDataMap.getDataMap());
    }

    private <T> T executeInternal(final SQLType sqlType, final BaseStatementUnit baseStatementUnit, final List<List<Object>> parameterSets, final ExecuteCallback<T> executeCallback,
                                  final boolean isExceptionThrown, final Map<String, Object> dataMap) throws Exception {
        synchronized (baseStatementUnit.getStatement().getConnection()) {
            T result;
            ExecutorExceptionHandler.setExceptionThrown(isExceptionThrown);
            ExecutorDataMap.setDataMap(dataMap);
            List<AbstractExecutionEvent> events = new LinkedList<>();
            if (parameterSets.isEmpty()) {
                events.add(getExecutionEvent(sqlType, baseStatementUnit, Collections.emptyList()));
            }

            parameterSets.stream().forEach(each -> {
                events.add(getExecutionEvent(sqlType, baseStatementUnit, each));
            });

            events.stream().forEach(event -> {
                EventBusInstance.getInstance().post(event);
            });


            try {
                result = executeCallback.execute(baseStatementUnit);
            } catch (final SQLException ex) {
                events.stream().forEach(each->{
                    each.setEventExecutionType(EventExecutionType.EXECUTE_FAILURE);
                    each.setException(Optional.of(ex));
                    EventBusInstance.getInstance().post(each);
                    ExecutorExceptionHandler.handleException(ex);
                });
                return null;
            }
            events.stream().forEach(each->{
                each.setEventExecutionType(EventExecutionType.EXECUTE_SUCCESS);
                EventBusInstance.getInstance().post(each);
            });
            return result;
        }
    }

    private AbstractExecutionEvent getExecutionEvent(final SQLType sqlType, final BaseStatementUnit baseStatementUnit, final List<Object> parameters) {
        AbstractExecutionEvent result;
        if (SQLType.DQL == sqlType) {
            result = new DQLExecutionEvent(baseStatementUnit.getSqlExecutionUnit().getDataSource(), baseStatementUnit.getSqlExecutionUnit().getSql(), parameters);
        } else {
            result = new DMLExecutionEvent(baseStatementUnit.getSqlExecutionUnit().getDataSource(), baseStatementUnit.getSqlExecutionUnit().getSql(), parameters);
        }
        return result;
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (final InterruptedException ignored) {
        }
        if (!executorService.isTerminated()) {
            throw new ShardingJdbcException("ExecutorEngine can not been terminated");
        }
    }
}
