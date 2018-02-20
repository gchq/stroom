package stroom.statistics.server.stroomstats.pipeline.appender;

import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.factory.PipelinePropertyDocRef;
import stroom.pipeline.server.writer.AbstractKafkaAppender;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.query.api.v2.DocRef;
import stroom.statistics.server.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.statistics.server.stroomstats.kafka.TopicNameFactory;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.util.shared.Severity;

import javax.inject.Inject;

/**
 * A Kafka appender specifically for sending statistic event messages to kafka.
 * The key and topic are derived from the selected statistic data source
 */
@SuppressWarnings("unused")
@ConfigurableElement(
        type = "StroomStatsAppender",
        category = PipelineElementType.Category.DESTINATION,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_DESTINATION,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.STROOM_STATS)
public class StroomStatsAppender extends AbstractKafkaAppender {
    private final TopicNameFactory topicNameFactory;
    private final StroomStatsStoreEntityService stroomStatsStoreEntityService;

    private String topic;
    private String recordKey;
    private DocRef stroomStatStoreRef;

    @SuppressWarnings("unused")
    @Inject
    public StroomStatsAppender(final ErrorReceiverProxy errorReceiverProxy,
                               final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                               final TopicNameFactory topicNameFactory,
                               final StroomStatsStoreEntityService stroomStatsStoreEntityService) {
        super(errorReceiverProxy, stroomKafkaProducerFactoryService);
        this.topicNameFactory = topicNameFactory;
        this.stroomStatsStoreEntityService = stroomStatsStoreEntityService;
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

        final StroomStatsStoreEntity stroomStatsStoreEntity = stroomStatsStoreEntityService.loadByUuid(stroomStatStoreRef.getUuid());

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
    @PipelinePropertyDocRef(types = StroomStatsStoreEntity.ENTITY_TYPE)
    public void setStatisticsDataSource(final DocRef stroomStatStoreRef) {
        this.stroomStatStoreRef = stroomStatStoreRef;
    }
}
