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

package com.dangdang.ddframe.rdb.sharding.spring.namespace.parser;

import com.dangdang.ddframe.rdb.sharding.api.strategy.slave.MasterSlaveLoadBalanceStrategyType;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.datasource.MasterSlaveDataSource;
import com.dangdang.ddframe.rdb.sharding.spring.namespace.constants.MasterSlaveDataSourceBeanDefinitionParserTag;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

/**
 * Master-slave data source parser for spring namespace.
 * 
 * @author zhangliang
 */
public class MasterSlaveDataSourceBeanDefinitionParser extends AbstractBeanDefinitionParser {
    
    @Override
    //CHECKSTYLE:OFF
    protected AbstractBeanDefinition parseInternal(final Element element, final ParserContext parserContext) {
    //CHECKSTYLE:ON
        BeanDefinitionBuilder factory = BeanDefinitionBuilder.rootBeanDefinition(MasterSlaveDataSource.class);
        factory.addConstructorArgValue(parseId(element));
        String masterDataSourceName = parseMasterDataSourceRef(element);
        factory.addConstructorArgValue(masterDataSourceName);
        factory.addConstructorArgReference(masterDataSourceName);
        factory.addConstructorArgValue(parseSlaveDataSources(element, parserContext));
        String strategyRef = parseStrategyRef(element);
        if (!Strings.isNullOrEmpty(strategyRef)) {
            factory.addConstructorArgReference(strategyRef);
        } else {
            factory.addConstructorArgValue(parseStrategyType(element));
        }
        return factory.getBeanDefinition();
    }
    
    private String parseId(final Element element) {
        return element.getAttribute(ID_ATTRIBUTE);
    }
    
    private String parseMasterDataSourceRef(final Element element) {
        return element.getAttribute(MasterSlaveDataSourceBeanDefinitionParserTag.MASTER_DATA_SOURCE_REF_ATTRIBUTE);
    }
    
    private Map<String, BeanDefinition> parseSlaveDataSources(final Element element, final ParserContext parserContext) {
        List<String> slaveDataSources = Splitter.on(",").trimResults().splitToList(element.getAttribute(MasterSlaveDataSourceBeanDefinitionParserTag.SLAVE_DATA_SOURCES_REF_ATTRIBUTE));
        Map<String, BeanDefinition> result = new ManagedMap<>(slaveDataSources.size());
        for (String each : slaveDataSources) {
            result.put(each, parserContext.getRegistry().getBeanDefinition(each));
        }
        return result;
    }
    
    private String parseStrategyRef(final Element element) {
        return element.getAttribute(MasterSlaveDataSourceBeanDefinitionParserTag.STRATEGY_REF_ATTRIBUTE);
    }
    
    private MasterSlaveLoadBalanceStrategyType parseStrategyType(final Element element) {
        String result = element.getAttribute(MasterSlaveDataSourceBeanDefinitionParserTag.STRATEGY_TYPE_ATTRIBUTE);
        return Strings.isNullOrEmpty(result) ? MasterSlaveLoadBalanceStrategyType.getDefaultStrategyType() : MasterSlaveLoadBalanceStrategyType.valueOf(result);
    }
}
