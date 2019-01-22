package stroom.guice;

import com.google.inject.AbstractModule;
import stroom.cache.CacheManagerLifecycleModule;
import stroom.cluster.impl.ClusterLifecycleModule;
import stroom.data.meta.impl.db.DataMetaDbLifecycleModule;
import stroom.entity.event.EntityEventLifecycleModule;
import stroom.importexport.ImportExportLifecycleModule;
import stroom.index.IndexLifecycleModule;
import stroom.jobsystem.JobSystemLifecycleModule;
import stroom.kafka.impl.KafkaLifecycleModule;
import stroom.persist.EntityManagerLifecycleModule;
import stroom.pipeline.destination.RollingDestinationsLifecycleModule;
import stroom.resource.ResourceLifecycleModule;
import stroom.search.SearchLifecycleModule;
import stroom.servicediscovery.ServiceDiscoveryLifecycleModule;
import stroom.statistics.internal.InternalStatisticsLifecycleModule;
import stroom.statistics.sql.SQLStatisticsLifecycleModule;
import stroom.streamtask.StreamTaskLifecycleModule;
import stroom.task.TaskManagerLifecycleModule;
import stroom.task.cluster.ClusterTaskLifecycleModule;

public class LifecycleModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CacheManagerLifecycleModule());
        install(new ClusterLifecycleModule());
        install(new ClusterTaskLifecycleModule());
        install(new DataMetaDbLifecycleModule());
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
