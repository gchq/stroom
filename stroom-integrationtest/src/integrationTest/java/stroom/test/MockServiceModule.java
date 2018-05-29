package stroom.test;

import com.google.inject.AbstractModule;

public class MockServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.cache.PipelineCacheModule());
        install(new stroom.dictionary.MockDictionaryModule());
        install(new stroom.docstore.memory.MemoryPersistenceModule());
        install(new stroom.entity.MockEntityModule());
        install(new stroom.explorer.MockExplorerModule());
        install(new stroom.guice.PipelineScopeModule());
        install(new stroom.importexport.ImportExportModule());
        install(new stroom.index.MockIndexModule());
        install(new stroom.node.MockNodeModule());
        install(new stroom.node.MockNodeServiceModule());
        install(new stroom.persist.MockPersistenceModule());
        install(new stroom.pipeline.MockPipelineModule());
        install(new stroom.pipeline.factory.FactoryModule());
        install(new stroom.pipeline.task.PipelineStreamTaskModule());
        install(new stroom.properties.MockPropertyModule());
        install(new stroom.refdata.ReferenceDataModule());
        install(new stroom.resource.MockResourceModule());
        install(new stroom.security.MockSecurityContextModule());
        install(new stroom.security.MockSecurityModule());
        install(new stroom.servlet.MockServletModule());
        install(new stroom.streamtask.MockStreamTaskModule());
        install(new stroom.statistics.internal.MockInternalStatisticsModule());
        install(new stroom.streamstore.MockStreamStoreModule());
        install(new stroom.task.MockTaskModule());
        install(new stroom.test.MockTestControlModule());
        install(new stroom.volume.MockVolumeModule());
        install(new stroom.xmlschema.MockXmlSchemaModule());
    }
}
