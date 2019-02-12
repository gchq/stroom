package stroom.app.guice;

import com.google.inject.AbstractModule;
import stroom.cache.impl.CacheManagerLifecycleModule;
import stroom.cluster.impl.ClusterLifecycleModule;
import stroom.meta.impl.db.MetaDbLifecycleModule;
import stroom.entity.event.EntityEventLifecycleModule;
import stroom.importexport.impl.ImportExportLifecycleModule;
import stroom.index.IndexLifecycleModule;
import stroom.job.JobSystemLifecycleModule;
import stroom.kafka.impl.KafkaLifecycleModule;
import stroom.persist.EntityManagerLifecycleModule;
import stroom.pipeline.destination.RollingDestinationsLifecycleModule;
import stroom.resource.impl.ResourceLifecycleModule;
import stroom.search.SearchLifecycleModule;
import stroom.servicediscovery.ServiceDiscoveryLifecycleModule;
import stroom.statistics.internal.InternalStatisticsLifecycleModule;
import stroom.statistics.sql.SQLStatisticsLifecycleModule;
import stroom.streamtask.StreamTaskLifecycleModule;
import stroom.task.impl.TaskManagerLifecycleModule;
import stroom.task.cluster.impl.ClusterTaskLifecycleModule;

public class LifecycleModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CacheManagerLifecycleModule());
        install(new ClusterLifecycleModule());
        install(new ClusterTaskLifecycleModule());
        install(new MetaDbLifecycleModule());
        install(new EntityEventLifecycleModule());
        install(new EntityManagerLifecycleModule());
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
