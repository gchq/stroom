/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.workspace;

import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.common.v2.DataSourceProviderRegistry;

import com.google.inject.AbstractModule;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;

/// Binds what [SourceDocAccess] needs to look up the type of a workspace file's source, for tests: every source is
/// an index, a data source type, so using what came from it needs Use, as before sources were checked by type.
public class MockSourceDocModule extends AbstractModule {

    /// The type every source is.
    public static final String SOURCE_TYPE = "Index";

    @Override
    protected void configure() {
        final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
        Mockito.when(explorerNodeService.getNodeByUuid(Mockito.anyString()))
                .thenAnswer(invocation -> Optional.of(ExplorerNode.builder()
                        .type(SOURCE_TYPE)
                        .uuid(invocation.getArgument(0))
                        .name("Source")
                        .build()));
        final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
        Mockito.when(registry.getDataSourceTypes()).thenReturn(Set.of(SOURCE_TYPE));
        bind(ExplorerNodeService.class).toInstance(explorerNodeService);
        bind(DataSourceProviderRegistry.class).toInstance(registry);
    }
}
