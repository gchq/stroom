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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import stroom.feed.FeedService;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.streamstore.StreamTypeService;
import stroom.guice.StroomBeanStore;

import javax.inject.Named;
import javax.inject.Provider;

@Configuration
public class LoggingSpringConfig {
    @Bean
    public AuthenticationEventLog authenticationEventLog(final StroomEventLoggingService eventLoggingService) {
        return new AuthenticationEventLog(eventLoggingService);
    }

    @Bean
    public AuthorisationEventLog authorisationEventLog(final StroomEventLoggingService eventLoggingService) {
        return new AuthorisationEventLog(eventLoggingService);
    }

    @Bean
    public BasicEventInfoProvider basicEventInfoProvider(@Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                                                         @Named("cachedFeedService") final FeedService feedService) {
        return new BasicEventInfoProvider(streamTypeService, feedService);
    }

    @Bean
    public DocumentEventLog documentEventLog(final StroomEventLoggingService eventLoggingService, final Provider<StroomBeanStore> stroomBeanStoreProvider) {
        return new DocumentEventLogImpl(eventLoggingService, stroomBeanStoreProvider);
    }

    @Bean
    public ImportExportEventLog importExportEventLog(final StroomEventLoggingService eventLoggingService) {
        return new ImportExportEventLog(eventLoggingService);
    }

    @Bean
    public StreamEventLog streamEventLog(final StroomEventLoggingService eventLoggingService) {
        return new StreamEventLog(eventLoggingService);
    }

    @Bean
    public StroomEventLoggingService stroomEventLoggingService(final SecurityContext security,
                                                               final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider) {
        HttpServletRequestHolder httpServletRequestHolder = null;
        try {
            httpServletRequestHolder = httpServletRequestHolderProvider.get();
        } catch (final Exception e) {
            // Ignore
        }

        return new StroomEventLoggingService(security, httpServletRequestHolder);
    }
}