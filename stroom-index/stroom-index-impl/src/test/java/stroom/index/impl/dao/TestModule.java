/*
 * Copyright 2019 Crown Copyright
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

package stroom.index.impl.dao;

import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docstore.mock.MockDocFinderModule;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexStore;
import stroom.index.mock.MockIndexVolumeGroupService;
import stroom.security.api.SecurityContext;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModule extends AbstractModule {

    static final String TEST_USER = "testUser";

    @Override
    protected void configure() {
        install(new DbTestModule());
        install(new MockDocFinderModule());
        install(new MockCollectionModule());
        install(new MockWordListProviderModule());

        // Create a test security context
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getUserIdentityForAudit())
                .thenReturn(TEST_USER);
        bind(SecurityContext.class).toInstance(securityContext);
        bind(IndexVolumeGroupService.class).toInstance(new MockIndexVolumeGroupService());
        bind(IndexStore.class).toInstance(mock(IndexStore.class));
    }
}
