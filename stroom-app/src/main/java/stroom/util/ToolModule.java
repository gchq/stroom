/*
 * Copyright 2018 Crown Copyright
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

package stroom.util;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.data.meta.impl.db.StreamStoreMetaDbModule;
import stroom.data.store.impl.fs.FSModule;
import stroom.data.store.StreamStoreModule;
import stroom.streamtask.statistic.MetaDataStatistic;
import stroom.statistics.internal.InternalStatisticEvent;
import stroom.statistics.internal.InternalStatisticsReceiver;

import java.util.List;

public class ToolModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new stroom.cache.CacheModule());
        install(new stroom.cache.PipelineCacheModule());
        install(new stroom.cluster.ClusterModule());
        install(new stroom.dictionary.DictionaryModule());
        install(new stroom.dictionary.DictionaryHandlerModule());
        install(new stroom.docstore.db.DBPersistenceModule());
        install(new stroom.document.DocumentModule());
        install(new stroom.entity.EntityModule());
        install(new stroom.entity.event.EntityEventModule());
        install(new stroom.entity.cluster.EntityClusterModule());
        install(new stroom.explorer.MockExplorerModule());
        install(new stroom.feed.FeedModule());
        install(new stroom.guice.PipelineScopeModule());
        install(new stroom.importexport.ImportExportModule());
        install(new stroom.jobsystem.JobSystemModule());
        install(new stroom.lifecycle.LifecycleModule());
        install(new stroom.logging.LoggingModule());
        install(new stroom.node.NodeModule());
        install(new stroom.node.NodeServiceModule());
        install(new stroom.persist.PersistenceModule());
        install(new stroom.pipeline.PipelineModule());
        install(new stroom.pipeline.factory.FactoryModule());
        install(new stroom.pipeline.stepping.PipelineSteppingModule());
        install(new stroom.pipeline.task.PipelineStreamTaskModule());
        install(new stroom.policy.PolicyModule());
        install(new stroom.properties.PropertyModule());
        install(new stroom.refdata.ReferenceDataModule());
        install(new stroom.resource.ResourceModule());
        install(new stroom.security.impl.mock.MockSecurityContextModule());
        install(new StreamStoreMetaDbModule());
        install(new StreamStoreModule());
        install(new FSModule());
        install(new stroom.streamtask.StreamTaskModule());
        install(new stroom.task.TaskModule());
        install(new stroom.task.cluster.ClusterTaskModule());
        install(new stroom.volume.VolumeModule());
        install(new stroom.xmlschema.XmlSchemaModule());
    }

    @Provides
    public InternalStatisticsReceiver internalStatisticsReceiver() {
        return new InternalStatisticsReceiver() {
            @Override
            public void putEvent(final InternalStatisticEvent event) {
            }

            @Override
            public void putEvents(final List<InternalStatisticEvent> events) {
            }
        };
    }

    @Provides
    public MetaDataStatistic metaDataStatistic() {
        return metaData -> {
        };
    }
}