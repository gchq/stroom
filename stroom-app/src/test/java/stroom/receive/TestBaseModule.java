/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.cache.service.impl.CacheServiceModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.core.receive.ReceiveDataModule;
import stroom.credentials.api.KeyStore;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.impl.db.MockCredentialsDaoModule;
import stroom.data.store.mock.MockStreamStoreModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.memory.MemoryPersistenceModule;
import stroom.documentation.impl.DocumentationModule;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.mock.MockStroomEventLoggingModule;
import stroom.explorer.impl.MockExplorerModule;
import stroom.feed.impl.FeedModule;
import stroom.gitrepo.mock.MockGitRepoModule;
import stroom.importexport.impl.ImportExportModule;
import stroom.meta.api.AttributeMap;
import stroom.meta.mock.MockMetaModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.node.mock.MockNodeServiceModule;
import stroom.pipeline.PipelineService;
import stroom.processor.api.ProcessorFilterService;
import stroom.receive.common.RequestAuthenticator;
import stroom.receive.rules.impl.ReceiveDataRuleSetModule;
import stroom.security.api.ContentPackUserService;
import stroom.security.api.UserIdentity;
import stroom.security.mock.MockSecurityContext;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.mock.MockSecurityModule;
import stroom.task.impl.TaskContextModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.HomeDirProviderImpl;
import stroom.util.io.PathConfig;
import stroom.util.io.StroomPathConfig;
import stroom.util.io.TempDirProvider;
import stroom.util.io.TempDirProviderImpl;
import stroom.util.pipeline.scope.PipelineScopeModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.util.Providers;
import jakarta.servlet.http.HttpServletRequest;

public class TestBaseModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new CacheModule());
        install(new CacheServiceModule());
        install(new DictionaryModule());
        install(new DocumentationModule());
        install(new DocStoreModule());
        install(new MockDocRefInfoModule());
        install(new FeedModule());
        install(new MockGitRepoModule());
        install(new MockCredentialsDaoModule());
        install(new ImportExportModule());
        install(new MemoryPersistenceModule());
        install(new MockClusterLockModule());
        install(new MockExplorerModule());
        install(new MockMetaModule());
        install(new MockMetaStatisticsModule());
        install(new MockMetricsModule());
        install(new MockNodeServiceModule());
        install(new MockSecurityModule());
        install(new MockSecurityContextModule());
        install(new MockStreamStoreModule());
        install(new MockStroomEventLoggingModule());
        install(new PipelineScopeModule());
        install(new ReceiveDataModule());
        install(new ReceiveDataRuleSetModule());
        install(new MockCollectionModule());
        install(new TaskContextModule());
        GuiceTestUtil.buildMockBinder(binder())
                .addMockBindingFor(PipelineService.class)
                .addMockBindingFor(ProcessorFilterService.class);

        bind(DocumentEventLog.class).toProvider(Providers.of(null));

        bind(HomeDirProvider.class).to(HomeDirProviderImpl.class);
        bind(ContentPackUserService.class).to(MockSecurityContext.class); //?
        bind(PathConfig.class).to(StroomPathConfig.class);
        bind(TempDirProvider.class).to(TempDirProviderImpl.class);

        bind(StoredSecrets.class).toInstance(new StoredSecrets() {
            @Override
            public StoredSecret get(final String name) {
                return null;
            }

            @Override
            public KeyStore getKeyStore(final String name) {
                return null;
            }
        });
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

//            @Override
//            public boolean hasAuthenticationToken(final HttpServletRequest request) {
//                return false;
//            }
//
//            @Override
//            public void removeAuthorisationEntries(final Map<String, String> headers) {
//
//            }
//
//            @Override
//            public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {
//                return Collections.emptyMap();
//            }
//
//            @Override
//            public Map<String, String> getServiceUserAuthHeaders() {
//                return Collections.emptyMap();
//            }
        };
    }
}
