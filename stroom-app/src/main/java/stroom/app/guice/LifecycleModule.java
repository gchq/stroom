package stroom.app.guice;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheManagerLifecycleModule;
import stroom.cluster.impl.ClusterLifecycleModule;
import stroom.cluster.task.impl.ClusterTaskLifecycleModule;
import stroom.core.entity.event.EntityEventLifecycleModule;
import stroom.importexport.impl.ImportExportLifecycleModule;
import stroom.index.IndexLifecycleModule;
import stroom.job.impl.JobSystemLifecycleModule;
import stroom.kafka.impl.KafkaLifecycleModule;
import stroom.meta.impl.db.MetaDbLifecycleModule;
import stroom.pipeline.destination.RollingDestinationsLifecycleModule;
import stroom.processor.impl.StreamTaskLifecycleModule;
import stroom.resource.impl.ResourceLifecycleModule;
import stroom.search.SearchLifecycleModule;
import stroom.servicediscovery.impl.ServiceDiscoveryLifecycleModule;
import stroom.statistics.impl.InternalStatisticsLifecycleModule;
import stroom.statistics.impl.sql.SQLStatisticsLifecycleModule;
import stroom.task.impl.TaskManagerLifecycleModule;

public class LifecycleModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CacheManagerLifecycleModule());
        install(new ClusterLifecycleModule());
        install(new ClusterTaskLifecycleModule());
        install(new MetaDbLifecycleModule());
        install(new EntityEventLifecycleModule());
        install(new ImportExportLifecycleModule());
        install(new IndexLifecycleModule());
        install(new InternalStatisticsLifecycleModule());
        install(new JobSystemLifecycleModule());
        install(new KafkaLifecycleModule());
        install(new ResourceLifecycleModule());
        install(new RollingDestinationsLifecycleModule());
        install(new SearchLifecycleModule());
        install(new ServiceDiscoveryLifecycleModule());
        install(new SQLStatisticsLifecycleModule());
        install(new StreamTaskLifecycleModule());
        install(new TaskManagerLifecycleModule());
    }
}
