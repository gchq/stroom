package stroom.statistics.server.stroomstats.pipeline.appender;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.writer.AbstractKafkaAppender;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.statistics.server.stroomstats.kafka.TopicNameFactory;
import stroom.stats.shared.StroomStatsStoreEntity;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@SuppressWarnings("unused")
@Component
@Scope(StroomScope.PROTOTYPE)
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
    private StroomStatsStoreEntity stroomStatsStoreEntity;
    private String topic = null;
    private String recordKey = null;

    @SuppressWarnings("unused")
    @Inject
    public StroomStatsAppender(final ErrorReceiverProxy errorReceiverProxy,
                               final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                               final TopicNameFactory topicNameFactory) {
        super(errorReceiverProxy, stroomKafkaProducerFactoryService);
        this.topicNameFactory = topicNameFactory;
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
        if (stroomStatsStoreEntity == null) {
            super.log(Severity.FATAL_ERROR, "Stroom-Stats data source has not been set", null);
            throw new LoggedException("Stroom-Stats data source has not been set");
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
    public void setStatisticsDataSource(final StroomStatsStoreEntity stroomStatsStoreEntity) {
        this.stroomStatsStoreEntity = stroomStatsStoreEntity;
    }
}
