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

package stroom.datafeed;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.datafeed.server.DataFeedRequestHandler;
import stroom.datafeed.server.DataFeedServlet;
import stroom.datafeed.server.MetaMapFilterFactory;
import stroom.datafeed.server.RequestHandler;
import stroom.feed.server.FeedService;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.util.spring.StroomScope;

import javax.inject.Named;
import javax.inject.Provider;

@Configuration
public class DataFeedSpringConfig {
    @Bean
    @Scope(StroomScope.PROTOTYPE)
    public DataFeedRequestHandler dataFeedRequestHandler(final SecurityContext securityContext,
                                                         final StreamStore streamStore,
                                                         @Named("cachedFeedService") final FeedService feedService,
                                                         final MetaDataStatistic metaDataStatistics,
                                                         final MetaMapFilterFactory metaMapFilterFactory,
                                                         final StroomPropertyService stroomPropertyService) {
        return new DataFeedRequestHandler(securityContext, streamStore, feedService, metaDataStatistics, metaMapFilterFactory, stroomPropertyService);
    }

    @Bean
    public DataFeedServlet dataFeedServlet(final Provider<RequestHandler> requestHandlerProvider) {
        return new DataFeedServlet(requestHandlerProvider);
    }
}