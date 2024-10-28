/*
 * Copyright 2024 Crown Copyright
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

package stroom.receive;

import stroom.cache.impl.CacheModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.core.receive.ReceiveDataModule;
import stroom.data.store.mock.MockStreamStoreModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.documentation.impl.DocumentationModule;
import stroom.event.logging.api.DocumentEventLog;
import stroom.feed.impl.FeedModule;
import stroom.legacy.impex_6_1.LegacyImpexModule;
import stroom.meta.api.AttributeMap;
import stroom.meta.mock.MockMetaModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.rules.impl.ReceiveDataRuleSetModule;
import stroom.security.api.UserIdentity;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.impl.TaskContextModule;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.pipeline.scope.PipelineScopeModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.util.Providers;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Map;

public class TestBaseModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new CacheModule());
        install(new DictionaryModule());
        install(new DocumentationModule());
        install(new DocStoreModule());
        install(new MockDocRefInfoModule());
        install(new FeedModule());
        install(new LegacyImpexModule());
        install(new MemoryPersistenceModule());
        install(new MockMetaModule());
        install(new MockMetaStatisticsModule());
        install(new MockSecurityContextModule());
        install(new MockStreamStoreModule());
        install(new PipelineScopeModule());
        install(new ReceiveDataModule());
        install(new ReceiveDataRuleSetModule());
        install(new MockCollectionModule());
        install(new TaskContextModule());

        bind(DocumentEventLog.class).toProvider(Providers.of(null));
    }

    @SuppressWarnings("unused")
    @Provides
    EntityEventBus entityEventBus() {
        return event -> {
        };
    }

    @SuppressWarnings("unused")
    @Provides
    RequestAuthenticator requestAuthenticator() {
        return new RequestAuthenticator() {
            @Override
            public UserIdentity authenticate(final HttpServletRequest request,
                                             final AttributeMap attributeMap) {
                return null;
            }

            @Override
            public boolean hasAuthenticationToken(final HttpServletRequest request) {
                return false;
            }

            @Override
            public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String> getServiceUserAuthHeaders() {
                return Collections.emptyMap();
            }
        };
    }
}
