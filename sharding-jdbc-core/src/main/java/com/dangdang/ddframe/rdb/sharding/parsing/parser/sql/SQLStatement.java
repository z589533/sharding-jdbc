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

package com.dangdang.ddframe.rdb.sharding.parsing.parser.sql;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Conditions;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.table.Tables;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.SQLToken;

import java.util.List;

/**
 * SQL statement.
 *
 * @author zhangliang
 */
public interface SQLStatement {
    
    /**
     * Get SQL type.
     *
     * @return SQL type
     */
    SQLType getType();
    
    /**
     * Get tables.
     * 
     * @return tables
     */
    Tables getTables();
    
    /**
     * Get conditions.
     *
     * @return conditions
     */
    Conditions getConditions();
    
    /**
     * Get SQL Tokens.
     * 
     * @return SQL Tokens
     */
    List<SQLToken> getSqlTokens();
    
    /**
     * Get index of parameters.
     *
     * @return index of parameters
     */
    int getParametersIndex();
    
    /**
     * Set index of parameters.
     *
     * @param parametersIndex index of parameters
     */
    void setParametersIndex(int parametersIndex);
    
    /**
     * 增加索引偏移量.
     *
     * @return 增加后的索引偏移量
     */
    int increaseParametersIndex();
}
