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

package com.dangdang.ddframe.rdb.sharding.jdbc.core.connection;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.hint.HintManagerHolder;
import com.dangdang.ddframe.rdb.sharding.jdbc.adapter.AbstractConnectionAdapter;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.ShardingContext;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.datasource.MasterSlaveDataSource;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.datasource.NamedDataSource;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.statement.ShardingPreparedStatement;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.statement.ShardingStatement;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Connection that support sharding.
 * 
 * @author zhangliang
 * @author gaohongtao
 */
@RequiredArgsConstructor
public final class ShardingConnection extends AbstractConnectionAdapter {
    
    @Getter
    private final ShardingContext shardingContext;
    
    /**
     * Get all database connections via data source name. 
     *
     * <p>Master-slave connection will return all actual connections</p>
     * 
     * @param dataSourceName data source name
     * @return all database connections via data source name
     * @throws SQLException SQL exception
     */
    public Collection<Connection> getAllConnections(final String dataSourceName) throws SQLException {
        DataSource dataSource = shardingContext.getShardingRule().getDataSourceRule().getDataSource(dataSourceName);
        Preconditions.checkState(null != dataSource, "Missing the rule of %s in DataSourceRule", dataSourceName);
        Collection<DataSource> dataSources = dataSource instanceof MasterSlaveDataSource
                ? ((MasterSlaveDataSource) dataSource).getAllDataSources().values() : Collections.singletonList(dataSource);
        Collection<Connection> result = new LinkedList<>();
        for (DataSource each : dataSources) {
            Connection connection = each.getConnection();
            replayMethodsInvocation(connection);
            result.add(connection);
        }
        return result;
    }
    
    /**
     * Get database connection via data source name.
     * 
     * @param dataSourceName data source name
     * @param sqlType SQL type
     * @return all database connections via data source name
     * @throws SQLException SQL exception
     */
    public Connection getConnection(final String dataSourceName, final SQLType sqlType) throws SQLException {
        if (getCachedConnections().containsKey(dataSourceName)) {
            return getCachedConnections().get(dataSourceName);
        }
        DataSource dataSource = shardingContext.getShardingRule().getDataSourceRule().getDataSource(dataSourceName);
        Preconditions.checkState(null != dataSource, "Missing the rule of %s in DataSourceRule", dataSourceName);
        String realDataSourceName;
        if (dataSource instanceof MasterSlaveDataSource) {
            NamedDataSource namedDataSource = ((MasterSlaveDataSource) dataSource).getDataSource(sqlType);
            realDataSourceName = namedDataSource.getName();
            if (getCachedConnections().containsKey(realDataSourceName)) {
                return getCachedConnections().get(realDataSourceName);
            }
            dataSource = namedDataSource.getDataSource();
        } else {
            realDataSourceName = dataSourceName;
        }
        Connection result = dataSource.getConnection();
        getCachedConnections().put(realDataSourceName, result);
        replayMethodsInvocation(result);
        return result;
    }
    
    /**
     * Release connection.
     *
     * @param connection to be released connection
     */
    public void release(final Connection connection) {
        getCachedConnections().values().remove(connection);
        try {
            connection.close();
        } catch (final SQLException ignored) {
        }
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return getConnection(shardingContext.getShardingRule().getDataSourceRule().getDataSourceNames().iterator().next(), SQLType.DQL).getMetaData();
    }


    @Override
    public String getCatalog() throws SQLException {
        return getConnection(shardingContext.getShardingRule().getDataSourceRule().getDataSourceNames().iterator().next(), SQLType.DQL).getCatalog();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        return new ShardingPreparedStatement(this, sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return new ShardingPreparedStatement(this, sql, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return new ShardingPreparedStatement(this, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        return new ShardingPreparedStatement(this, sql, autoGeneratedKeys);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        return new ShardingPreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        return new ShardingPreparedStatement(this, sql, Statement.RETURN_GENERATED_KEYS);
    }
    
    @Override
    public Statement createStatement() throws SQLException {
        return new ShardingStatement(this);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return new ShardingStatement(this, resultSetType, resultSetConcurrency);
    }
    
    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return new ShardingStatement(this, resultSetType, resultSetConcurrency, resultSetHoldability);
    }
    
    @Override
    public void close() throws SQLException {
        HintManagerHolder.clear();
        MasterSlaveDataSource.resetDMLFlag();
        super.close();
    }
}
