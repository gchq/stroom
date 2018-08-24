package stroom.test;

import com.google.inject.AbstractModule;
import stroom.pipeline.scope.PipelineScopeModule;

public class MockServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.data.meta.impl.mock.MockDataMetaModule());
        install(new stroom.cache.PipelineCacheModule());
        install(new stroom.feed.MockFeedModule());
        install(new stroom.dictionary.MockDictionaryModule());
        install(new stroom.docstore.memory.MemoryPersistenceModule());
//        install(new stroom.entity.MockEntityModule());
        install(new stroom.explorer.MockExplorerModule());
        install(new PipelineScopeModule());
        install(new stroom.importexport.ImportExportModule());
        install(new stroom.index.MockIndexModule());
        install(new stroom.node.MockNodeServiceModule());
        install(new stroom.persist.MockPersistenceModule());
        install(new stroom.pipeline.PipelineModule());
        install(new stroom.pipeline.factory.PipelineFactoryModule());
        install(new stroom.pipeline.factory.CommonPipelineElementModule());
        install(new stroom.pipeline.factory.DataStorePipelineElementModule());
        install(new stroom.pipeline.xsltfunctions.CommonXsltFunctionModule());
        install(new stroom.pipeline.xsltfunctions.DataStoreXsltFunctionModule());
        install(new stroom.pipeline.task.PipelineStreamTaskModule());
//        install(new stroom.properties.impl.mock.MockPropertyModule());
        install(new stroom.refdata.ReferenceDataModule());
        install(new stroom.resource.MockResourceModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
        install(new stroom.security.MockSecurityModule());
        install(new stroom.servlet.MockServletModule());
        install(new stroom.streamtask.MockStreamTaskModule());
        install(new stroom.statistics.internal.MockInternalStatisticsModule());
        install(new stroom.data.store.impl.fs.MockStreamStoreModule());
        install(new stroom.task.MockTaskModule());
        install(new stroom.test.MockTestControlModule());
        install(new stroom.volume.MockVolumeModule());
        install(new stroom.xmlschema.MockXmlSchemaModule());
    }
}
