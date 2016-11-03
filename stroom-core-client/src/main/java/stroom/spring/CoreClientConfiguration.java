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

import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import stroom.cluster.server.ClusterCallServiceRPC;
import stroom.datafeed.server.DataFeedServiceImpl;
import stroom.dispatch.client.DispatchService;
import stroom.dispatch.server.DispatchServiceImpl;
import stroom.entity.server.SpringRequestFactoryServlet;
import stroom.feed.server.RemoteFeedServiceRPC;
import stroom.servlet.*;
import stroom.util.config.StroomProperties;
import stroom.util.logging.StroomLogger;
import stroom.util.thread.ThreadLocalBuffer;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.HeaderMapFactory;

import java.util.Properties;

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {"stroom"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),})
public class CoreClientConfiguration {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(CoreClientConfiguration.class);

    public CoreClientConfiguration() {
        LOGGER.info("CoreClientConfiguration loading...");
    }

    @Bean
    @Scope("request")
    public HeaderMap headerMap() {
        return new HeaderMapFactory().create();
    }

    @Bean
    @Scope("request")
    public ThreadLocalBuffer requestThreadLocalBuffer() {
        final ThreadLocalBuffer threadLocalBuffer = new ThreadLocalBuffer();
        threadLocalBuffer.setBufferSize(StroomProperties.getProperty("stroom.buffersize"));
        return threadLocalBuffer;
    }


    @Bean
    public SimpleUrlHandlerMapping urlMapping() {
        final SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setLazyInitHandlers(true);
        mapping.setAlwaysUseFullPath(true);

        final Properties mappings = new Properties();
        mappings.setProperty("/stroom/dynamic.css", DynamicCSSServlet.BEAN_NAME);
        mappings.setProperty("/stroom/dispatch.rpc", "dispatchServiceRPC");
        mappings.setProperty("/stroom/importfile.rpc", ImportFileServlet.BEAN_NAME);
        mappings.setProperty("/stroom/script", "scriptServlet");
        mappings.setProperty("/clustercall.rpc", ClusterCallServiceRPC.BEAN_NAME);
        mappings.setProperty("/export/*", ExportConfigServlet.BEAN_NAME);
        mappings.setProperty("/status", StatusServlet.BEAN_NAME);
        mappings.setProperty("/echo", EchoServlet.BEAN_NAME);
        mappings.setProperty("/debug", DebugServlet.BEAN_NAME);
        mappings.setProperty("/sessionList", SessionListServlet.BEAN_NAME);
        mappings.setProperty("/resourcestore/*", SessionResourceStoreImpl.BEAN_NAME);
        mappings.setProperty("/gwtRequest", SpringRequestFactoryServlet.BEAN_NAME);

        // Normally Stroom Proxy Serves This
        mappings.setProperty("/remoting/remotefeedservice.rpc", RemoteFeedServiceRPC.BEAN_NAME);
        mappings.setProperty("/datafeed", DataFeedServiceImpl.BEAN_NAME);
        mappings.setProperty("/datafeed/*", DataFeedServiceImpl.BEAN_NAME);
        mapping.setMappings(mappings);

        return mapping;
    }

    @Bean
    public ProxyFactoryBean dispatchServiceRPC() throws ClassNotFoundException {
        return getProxyFactoryBean(DispatchService.class, DispatchServiceImpl.BEAN_NAME, "jpaDeProxyMethodInterceptor");
    }

    private ProxyFactoryBean getProxyFactoryBean(final Class<?> clazz, final String targetName,
                                                 final String interceptorName) throws ClassNotFoundException {
        final ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.setProxyInterfaces(new Class<?>[]{clazz});
        proxyFactoryBean.setTargetName(targetName);
        proxyFactoryBean.setInterceptorNames(interceptorName);
        return proxyFactoryBean;
    }
}
