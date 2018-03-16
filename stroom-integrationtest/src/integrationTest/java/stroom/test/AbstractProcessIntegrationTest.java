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

package stroom.test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import stroom.guice.PipelineScopeModule;

public abstract class AbstractProcessIntegrationTest extends StroomIntegrationTest {
    private static final Injector injector;

    static {
        injector = Guice.createInjector(
                new stroom.entity.MockEntityModule(),
                new stroom.security.MockSecurityContextModule(),
                new stroom.dictionary.MockDictionaryModule(),
                new stroom.docstore.memory.MemoryPersistenceModule(),
                new stroom.persist.MockPersistenceModule(),
                new stroom.properties.MockPropertyModule(),
                new stroom.importexport.ImportExportModule(),
                new stroom.explorer.MockExplorerModule(),
                new stroom.servlet.MockServletModule(),
                new stroom.test.MockTestControlModule(),
                new stroom.index.MockIndexModule(),
                new stroom.node.MockNodeServiceModule(),
                new stroom.node.MockNodeModule(),
                new stroom.volume.MockVolumeModule(),
                new stroom.statistics.internal.MockInternalStatisticsModule(),
                new stroom.streamstore.MockStreamStoreModule(),
                new stroom.streamtask.MockStreamTaskModule(),
                new stroom.task.MockTaskModule(),
                new stroom.pipeline.MockPipelineModule(),
                new stroom.cache.PipelineCacheModule(),
                new stroom.pipeline.task.PipelineStreamTaskModule(),
                new stroom.feed.MockFeedModule(),
                new stroom.refdata.ReferenceDataModule(),
                new stroom.security.MockSecurityModule(),
                new stroom.pipeline.factory.FactoryModule(),
                new stroom.guice.PipelineScopeModule(),
                new stroom.resource.MockResourceModule(),
                new stroom.xmlschema.MockXmlSchemaModule()
        );
    }

    @Before
    public void before() {
//        final Injector childInjector = injector.createChildInjector();
//        childInjector.injectMembers(this);

        injector.injectMembers(this);
        super.before();
        super.importSchemas(true);
    }
}