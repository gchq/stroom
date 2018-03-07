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

package stroom.logging;

import com.google.inject.AbstractModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.feed.FeedService;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.streamstore.StreamTypeService;
import stroom.util.spring.StroomBeanStore;

import javax.inject.Named;
import javax.inject.Provider;

public class LoggingModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DocumentEventLog.class).to(DocumentEventLogImpl.class);
    }



//    @Bean
//    public StroomEventLoggingService stroomEventLoggingService(final SecurityContext security,
//                                                               final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider) {
//        HttpServletRequestHolder httpServletRequestHolder = null;
//        try {
//            httpServletRequestHolder = httpServletRequestHolderProvider.get();
//        } catch (final Exception e) {
//            // Ignore
//        }
//
//        return new StroomEventLoggingService(security, httpServletRequestHolder);
//    }
}