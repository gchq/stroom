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

package stroom.feed.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.entity.server.CachingEntityManager;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.util.spring.StroomScope;

import javax.inject.Named;


@Configuration
public class FeedSpringConfig {
    @Bean("cachedFeedService")
    public CachedFeedService cachedFeedService(final CachingEntityManager entityManager,
                                               final ImportExportHelper importExportHelper,
                                               final SecurityContext securityContext) {
        return new CachedFeedService(entityManager, importExportHelper, securityContext);
    }

    @Bean("feedService")
    public FeedService feedService(final StroomEntityManager entityManager,
                                   final ImportExportHelper importExportHelper,
                                   final SecurityContext securityContext) {
        return new FeedServiceImpl(entityManager, importExportHelper, securityContext);
    }

    @Bean
    @Scope(value = StroomScope.TASK)
    public FetchSupportedEncodingsActionHandler fetchSupportedEncodingsActionHandler() {
        return new FetchSupportedEncodingsActionHandler();
    }

    @Bean("remoteFeedService")
    public RemoteFeedService remoteFeedService(final SecurityContext securityContext, @Named("cachedFeedService") final FeedService feedService) {
        return new RemoteFeedServiceImpl(securityContext, feedService);
    }

    @Bean
    public RemoteFeedServiceRPC remoteFeedServiceRPC(@Named("remoteFeedService") final RemoteFeedService remoteFeedService) {
        return new RemoteFeedServiceRPC(remoteFeedService);
    }
}