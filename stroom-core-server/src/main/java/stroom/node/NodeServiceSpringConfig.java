/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.node;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.entity.CachingEntityManager;
import stroom.entity.StroomEntityManager;

@Configuration
public class NodeServiceSpringConfig {
    @Bean("nodeService")
    public NodeService nodeService(final StroomEntityManager entityManager,
                                   final NodeServiceTransactionHelper nodeServiceTransactionHelper,
                                   @Value("#{propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
                                   @Value("#{propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
        return new NodeServiceImpl(entityManager, nodeServiceTransactionHelper, nodeName, rackName);
    }

    @Bean("cachedNodeService")
    public NodeService cachedNodeService(final CachingEntityManager entityManager,
                                         final NodeServiceTransactionHelper nodeServiceTransactionHelper,
                                         @Value("#{propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
                                         @Value("#{propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
        return new NodeServiceImpl(entityManager, nodeServiceTransactionHelper, nodeName, rackName);
    }

    @Bean
    public NodeServiceTransactionHelper nodeServiceTransactionHelper(final StroomEntityManager entityManager) {
        return new NodeServiceTransactionHelper(entityManager);
    }
}