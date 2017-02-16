/*
 * Copyright 2016 Crown Copyright
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

package stroom.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import stroom.entity.shared.FolderService;
import stroom.feed.shared.FeedService;
import stroom.node.shared.NodeService;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.streamstore.shared.StreamTypeService;
import stroom.streamtask.shared.StreamProcessorFilterService;
import stroom.streamtask.shared.StreamProcessorService;

/**
 * Configures @Beans for all cached services.
 */
@Configuration
public class CachedServiceConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedServiceConfiguration.class);

    public CachedServiceConfiguration() {
        LOGGER.info("CachedServiceConfiguration loading...");
    }

    @Bean
    public ProxyFactoryBean cachedFeedService() throws ClassNotFoundException {
        return getProxyFactoryBean(FeedService.class, "feedService", "serviceCacheInterceptor");
    }

    @Bean
    public ProxyFactoryBean cachedFolderService() throws ClassNotFoundException {
        return getProxyFactoryBean(FolderService.class, "folderService", "serviceCacheInterceptor");
    }

    @Bean
    public ProxyFactoryBean cachedNodeService() throws ClassNotFoundException {
        return getProxyFactoryBean(NodeService.class, "nodeService", "serviceCacheInterceptor");
    }

    @Bean
    public ProxyFactoryBean cachedStreamTypeService() throws ClassNotFoundException {
        return getProxyFactoryBean(StreamTypeService.class, "streamTypeService", "serviceCacheInterceptor");
    }

    @Bean
    public ProxyFactoryBean cachedStreamProcessorService() throws ClassNotFoundException {
        return getProxyFactoryBean(StreamProcessorService.class, "streamProcessorService", "serviceCacheInterceptor");
    }

    @Bean
    public ProxyFactoryBean cachedStreamProcessorFilterService() throws ClassNotFoundException {
        return getProxyFactoryBean(StreamProcessorFilterService.class, "streamProcessorFilterService",
                "serviceCacheInterceptor");
    }

    @Bean
    public ProxyFactoryBean cachedPipelineEntityService() throws ClassNotFoundException {
        return getProxyFactoryBean(PipelineEntityService.class, "pipelineEntityService", "serviceCacheInterceptor");
    }

    private ProxyFactoryBean getProxyFactoryBean(final Class<?> clazz, final String targetName,
            final String interceptorName) throws ClassNotFoundException {
        final ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.setProxyInterfaces(new Class<?>[] { clazz });
        proxyFactoryBean.setTargetName(targetName);
        proxyFactoryBean.setInterceptorNames(interceptorName);
        return proxyFactoryBean;
    }
}
