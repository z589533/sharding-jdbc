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

package com.dangdang.ddframe.rdb.sharding.parsing.parser.dialect.mysql.clause;

import com.dangdang.ddframe.rdb.sharding.parsing.lexer.LexerEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.dialect.mysql.MySQLKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Literals;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.Symbol;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.limit.Limit;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.limit.LimitValue;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.exception.SQLParsingException;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.clause.SQLClauseParser;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dql.select.SelectStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.OffsetToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.RowCountToken;
import lombok.RequiredArgsConstructor;

/**
 * Limit clause parser for MySQL.
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
public final class MySQLLimitClauseParser implements SQLClauseParser {
    
    private final LexerEngine lexerEngine;
    
    /**
     * Parse limit.
     * 
     * @param selectStatement select statement
     */
    public void parse(final SelectStatement selectStatement) {
        if (!lexerEngine.skipIfEqual(MySQLKeyword.LIMIT)) {
            return;
        }
        int valueIndex = -1;
        int valueBeginPosition = lexerEngine.getCurrentToken().getEndPosition();
        int value;
        boolean isParameterForValue = false;
        if (lexerEngine.equalAny(Literals.INT)) {
            value = Integer.parseInt(lexerEngine.getCurrentToken().getLiterals());
            valueBeginPosition = valueBeginPosition - (value + "").length();
        } else if (lexerEngine.equalAny(Symbol.QUESTION)) {
            valueIndex = selectStatement.getParametersIndex();
            value = -1;
            valueBeginPosition--;
            isParameterForValue = true;
        } else {
            throw new SQLParsingException(lexerEngine);
        }
        lexerEngine.nextToken();
        if (lexerEngine.skipIfEqual(Symbol.COMMA)) {
            selectStatement.setLimit(getLimitWithComma(valueIndex, valueBeginPosition, value, isParameterForValue, selectStatement));
            return;
        }
        if (lexerEngine.skipIfEqual(MySQLKeyword.OFFSET)) {
            selectStatement.setLimit(getLimitWithOffset(valueIndex, valueBeginPosition, value, isParameterForValue, selectStatement));
            return;
        }
        if (!isParameterForValue) {
            selectStatement.getSqlTokens().add(new RowCountToken(valueBeginPosition, value));
        }
        Limit limit = new Limit(true);
        limit.setRowCount(new LimitValue(value, valueIndex));
        selectStatement.setLimit(limit);
    }
    
    private Limit getLimitWithComma(final int index, final int valueBeginPosition, final int value, final boolean isParameterForValue, final SelectStatement selectStatement) {
        int rowCountBeginPosition = lexerEngine.getCurrentToken().getEndPosition();
        int rowCountValue;
        int rowCountIndex = -1;
        boolean isParameterForRowCount = false;
        if (lexerEngine.equalAny(Literals.INT)) {
            rowCountValue = Integer.parseInt(lexerEngine.getCurrentToken().getLiterals());
            rowCountBeginPosition = rowCountBeginPosition - (rowCountValue + "").length();
        } else if (lexerEngine.equalAny(Symbol.QUESTION)) {
            rowCountIndex = -1 == index ? selectStatement.getParametersIndex() : index + 1;
            rowCountValue = -1;
            rowCountBeginPosition--;
            isParameterForRowCount = true;
        } else {
            throw new SQLParsingException(lexerEngine);
        }
        lexerEngine.nextToken();
        if (!isParameterForValue) {
            selectStatement.getSqlTokens().add(new OffsetToken(valueBeginPosition, value));
        }
        if (!isParameterForRowCount) {
            selectStatement.getSqlTokens().add(new RowCountToken(rowCountBeginPosition, rowCountValue));
        }
        Limit result = new Limit(true);
        result.setRowCount(new LimitValue(rowCountValue, rowCountIndex));
        result.setOffset(new LimitValue(value, index));
        return result;
    }
    
    private Limit getLimitWithOffset(final int index, final int valueBeginPosition, final int value, final boolean isParameterForValue, final SelectStatement selectStatement) {
        int offsetBeginPosition = lexerEngine.getCurrentToken().getEndPosition();
        int offsetValue = -1;
        int offsetIndex = -1;
        boolean isParameterForOffset = false;
        if (lexerEngine.equalAny(Literals.INT)) {
            offsetValue = Integer.parseInt(lexerEngine.getCurrentToken().getLiterals());
            offsetBeginPosition = offsetBeginPosition - (offsetValue + "").length();
        } else if (lexerEngine.equalAny(Symbol.QUESTION)) {
            offsetIndex = -1 == index ? selectStatement.getParametersIndex() : index + 1;
            offsetBeginPosition--;
            isParameterForOffset = true;
        } else {
            throw new SQLParsingException(lexerEngine);
        }
        lexerEngine.nextToken();
        if (!isParameterForOffset) {
            selectStatement.getSqlTokens().add(new OffsetToken(offsetBeginPosition, offsetValue));
        }
        if (!isParameterForValue) {
            selectStatement.getSqlTokens().add(new RowCountToken(valueBeginPosition, value));
        }
        Limit result = new Limit(true);
        result.setRowCount(new LimitValue(value, index));
        result.setOffset(new LimitValue(offsetValue, offsetIndex));
        return result;
    }
}
