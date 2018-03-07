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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import stroom.entity.CachingEntityManager;
import stroom.entity.StroomEntityManager;
import stroom.properties.StroomPropertyService;

public class NodeServiceModule extends AbstractModule {
    @Override
    protected void configure() {
//        bind(NodeServiceImpl.class).in(Singleton.class);
        bind(NodeService.class).to(NodeServiceImpl.class);
        bind(NodeServiceGetDefaultNode.class).to(NodeServiceImpl.class);
    }

//    @Provides
//    public NodeService nodeServiceImpl(final StroomEntityManager entityManager,
//                                           final NodeServiceTransactionHelper nodeServiceTransactionHelper,
//                                           @Value(propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
//                                           @Value(propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
//        return new NodeServiceImpl(entityManager, nodeServiceTransactionHelper, nodeName, rackName);
//    }

    @Provides
    @Named("cachedNodeService")
    public NodeService cachedNodeService(final CachingEntityManager entityManager,
                                         final NodeServiceTransactionHelper nodeServiceTransactionHelper,
                                         final StroomPropertyService propertyService) {
        return new NodeServiceImpl(entityManager, nodeServiceTransactionHelper, propertyService);
    }

//    @Bean("nodeService")
//    public NodeServiceImpl nodeServiceImpl(final StroomEntityManager entityManager,
//                                           final NodeServiceTransactionHelper nodeServiceTransactionHelper,
//                                           @Value(propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
//                                           @Value(propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
//        return new NodeServiceImpl(entityManager, nodeServiceTransactionHelper, nodeName, rackName);
//    }
//
//    @Bean("cachedNodeService")
//    public NodeService cachedNodeService(final CachingEntityManager entityManager,
//                                         final NodeServiceTransactionHelper nodeServiceTransactionHelper,
//                                         @Value(propertyConfigurer.getProperty('stroom.node')}") final String nodeName,
//                                         @Value(propertyConfigurer.getProperty('stroom.rack')}") final String rackName) {
//        return new NodeServiceImpl(entityManager, nodeServiceTransactionHelper, nodeName, rackName);
//    }
//
//    @Bean
//    public NodeServiceTransactionHelper nodeServiceTransactionHelper(final StroomEntityManager entityManager) {
//        return new NodeServiceTransactionHelper(entityManager);
//    }
}