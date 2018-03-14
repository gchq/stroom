package stroom.util;

import com.google.inject.Guice;
import com.google.inject.Injector;
import stroom.guice.PipelineScopeModule;

public class ToolInjector {
    private Injector injector = null;

    Injector getInjector() {
        if (injector == null) {
            injector = createInjector();
        }
        return injector;
    }

    private Injector createInjector() {
        final Injector injector = Guice.createInjector(
                new stroom.entity.EntityModule(),
                new stroom.security.MockSecurityContextModule(),
                new stroom.dictionary.DictionaryStoreModule(),
                new stroom.dictionary.DictionaryHandlerModule(),
                new stroom.docstore.db.DBPersistenceModule(),
                new stroom.spring.PersistenceModule(),
                new stroom.properties.PropertyModule(),
                new stroom.importexport.ImportExportModule(),
                new stroom.explorer.MockExplorerModule(),
                new stroom.cache.CacheModule(),
                new stroom.node.NodeServiceModule(),
                new stroom.node.NodeModule(),
                new stroom.volume.VolumeModule(),
                new stroom.streamstore.StreamStoreModule(),
                new stroom.streamstore.fs.FSModule(),
                new stroom.streamtask.StreamTaskModule(),
                new stroom.task.TaskModule(),
                new stroom.task.cluster.ClusterTaskModule(),
                new stroom.cluster.ClusterModule(),
                new stroom.jobsystem.JobSystemModule(),
                new stroom.pipeline.PipelineModule(),
                new stroom.cache.PipelineCacheModule(),
                new stroom.pipeline.stepping.PipelineSteppingModule(),
                new stroom.pipeline.task.PipelineStreamTaskModule(),
                new stroom.document.DocumentModule(),
                new stroom.entity.cluster.EntityClusterModule(),
                new stroom.entity.event.EntityEventModule(),
                new stroom.feed.FeedModule(),
                new stroom.lifecycle.LifecycleModule(),
                new stroom.policy.PolicyModule(),
                new stroom.refdata.ReferenceDataModule(),
                new stroom.logging.LoggingModule(),
                new stroom.pipeline.factory.FactoryModule(),
                new PipelineScopeModule(),
                new stroom.resource.ResourceModule(),
                new stroom.xmlschema.XmlSchemaModule(),
                new ToolModule()
        );
        injector.injectMembers(this);

        return injector;
    }
}
