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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import stroom.node.shared.Node;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomScope;
import stroom.util.thread.ThreadLocalBuffer;

/**
 * Defines the application context configuration for the server module.
 */
@Configuration
@EnableAspectJAutoProxy
public class ServerConfiguration {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public ThreadLocalBuffer prototypeThreadLocalBuffer() {
        final ThreadLocalBuffer threadLocalBuffer = new ThreadLocalBuffer();
        threadLocalBuffer.setBufferSize(StroomProperties.getProperty("stroom.buffersize"));
        return threadLocalBuffer;
    }

    @Bean
    @Lazy
    @Scope(StroomScope.THREAD)
    public Node sourceNode() {
        return new Node();
    }

    @Bean
    @Lazy
    @Scope(StroomScope.THREAD)
    public Node targetNode() {
        return new Node();
    }

    @Bean
    public StroomBeanStore stroomBeanStore() {
        return new StroomBeanStore();
    }
}
