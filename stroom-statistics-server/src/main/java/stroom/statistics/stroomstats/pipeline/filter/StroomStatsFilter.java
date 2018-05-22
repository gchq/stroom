package stroom.statistics.stroomstats.pipeline.filter;

import stroom.kafka.StroomKafkaProducerFactoryService;
import stroom.kafka.AbstractKafkaProducerFilter;
import stroom.properties.StroomPropertyService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggedException;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.factory.PipelinePropertyDocRef;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.query.api.v2.DocRef;
import stroom.statistics.stroomstats.entity.StroomStatsStoreStore;
import stroom.statistics.stroomstats.kafka.TopicNameFactory;
import stroom.stats.shared.StroomStatsStoreDoc;
import stroom.util.shared.Severity;

import javax.inject.Inject;

@SuppressWarnings("unused")
@ConfigurableElement(
        type = "StroomStatsFilter",
        category = PipelineElementType.Category.FILTER,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.STROOM_STATS)
public class StroomStatsFilter extends AbstractKafkaProducerFilter {

    private final TopicNameFactory topicNameFactory;
    private final StroomStatsStoreStore stroomStatsStoreStore;

    private String topic;
    private String recordKey;
    private DocRef stroomStatStoreRef;

    @Inject
    StroomStatsFilter(final ErrorReceiverProxy errorReceiverProxy,
                             final LocationFactoryProxy locationFactory,
                             final StroomPropertyService stroomPropertyService,
                             final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                             final TopicNameFactory topicNameFactory,
                             final StroomStatsStoreStore stroomStatsStoreStore) {
        super(errorReceiverProxy, locationFactory, stroomKafkaProducerFactoryService);
        this.topicNameFactory = topicNameFactory;
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

    @Override
    public void startProcessing() {
        if (stroomStatStoreRef == null) {
            super.log(Severity.FATAL_ERROR, "Stroom-Stats data source has not been set", null);
            throw new LoggedException("Stroom-Stats data source has not been set");
        }

        final StroomStatsStoreDoc stroomStatsStoreEntity = stroomStatsStoreStore.readDocument(stroomStatStoreRef);

        if (stroomStatsStoreEntity == null) {
            super.log(Severity.FATAL_ERROR, "Unable to find Stroom-Stats data source " + stroomStatStoreRef, null);
            throw new LoggedException("Unable to find Stroom-Stats data source " + stroomStatStoreRef);
        }

        if (!stroomStatsStoreEntity.isEnabled()) {
            final String msg = "Stroom-Stats data source with name [" + stroomStatsStoreEntity.getName() + "] is disabled";
            log(Severity.FATAL_ERROR, msg, null);
            throw new LoggedException(msg);
        }

        topic = topicNameFactory.getTopic(stroomStatsStoreEntity.getStatisticType());
        recordKey = stroomStatsStoreEntity.getUuid();

        super.startProcessing();
    }

    @PipelineProperty(description = "The stroom-stats data source to record statistics against.")
    @PipelinePropertyDocRef(types = StroomStatsStoreDoc.DOCUMENT_TYPE)
    public void setStatisticsDataSource(final DocRef stroomStatStoreRef) {
        this.stroomStatStoreRef = stroomStatStoreRef;
    }
}
