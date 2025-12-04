/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.statistics.impl.hbase.pipeline;

import stroom.docref.DocRef;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.statistics.impl.hbase.entity.StroomStatsStoreStore;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;

@SuppressWarnings("unused")
@ConfigurableElement(
        type = "StroomStatsFilter",
        description = """
                An element to allow the source data (conforming to the `statistics` XML Schema) \
                to be sent to an external stroom-stats service.
                """,
        category = PipelineElementType.Category.FILTER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_STROOM_STATS)
class StroomStatsFilter extends AbstractKafkaProducerFilter {

    private final HBaseStatisticsConfig hBaseStatisticsConfig;
    private final StroomStatsStoreStore stroomStatsStoreStore;

    private String topic;
    private String recordKey;
    private DocRef stroomStatStoreRef;

    @Inject
    StroomStatsFilter(final ErrorReceiverProxy errorReceiverProxy,
                      final LocationFactoryProxy locationFactory,
                      final KafkaProducerFactory kafkaProducerFactory,
                      final HBaseStatisticsConfig hBaseStatisticsConfig,
                      final StroomStatsStoreStore stroomStatsStoreStore) {
        super(errorReceiverProxy, locationFactory, kafkaProducerFactory);
        this.hBaseStatisticsConfig = hBaseStatisticsConfig;
        this.stroomStatsStoreStore = stroomStatsStoreStore;

    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public String getRecordKey() {
        return recordKey;
    }

    /*
    Warning! This software has not been tested recently and is likely to need some rework as a number of things
    have changed around it.
    todo test and fix as appropriate!
     */
    @Override
    public void startProcessing() {
        if (stroomStatStoreRef == null) {
            super.log(Severity.FATAL_ERROR, "Stroom-Stats data source has not been set", null);
            throw LoggedException.create("Stroom-Stats data source has not been set");
        }

        final StroomStatsStoreDoc stroomStatsStoreEntity = stroomStatsStoreStore.readDocument(stroomStatStoreRef);

        if (stroomStatsStoreEntity == null) {
            super.log(Severity.FATAL_ERROR, "Unable to find Stroom-Stats data source " + stroomStatStoreRef, null);
            throw LoggedException.create("Unable to find Stroom-Stats data source " + stroomStatStoreRef);
        }

        if (!stroomStatsStoreEntity.isEnabled()) {
            final String msg = "Stroom-Stats data source with name [" + stroomStatsStoreEntity.getName() +
                    "] is disabled";
            log(
                    Severity.FATAL_ERROR,
                    msg,
                    null);
            throw LoggedException.create(msg);
        }

        switch (stroomStatsStoreEntity.getStatisticType()) {
            case COUNT:
                topic = hBaseStatisticsConfig.getKafkaTopicsConfig().getCount();
                break;
            case VALUE:
                topic = hBaseStatisticsConfig.getKafkaTopicsConfig().getValue();
                break;
        }

        recordKey = stroomStatsStoreEntity.getUuid();

        super.startProcessing();
    }

    @PipelineProperty(description = "The stroom-stats data source to record statistics against.", displayPriority = 1)
    @PipelinePropertyDocRef(types = StroomStatsStoreDoc.TYPE)
    public void setStatisticsDataSource(final DocRef stroomStatStoreRef) {
        this.stroomStatStoreRef = stroomStatStoreRef;
    }
}
