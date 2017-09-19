package stroom.statistics.server.stroomstats.pipeline.filter;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.filter.AbstractKafkaProducerFilter;
import stroom.node.server.StroomPropertyService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggedException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.writer.PathCreator;
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
        type = "StroomStatsFilter",
        category = PipelineElementType.Category.FILTER,
        roles = {PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = ElementIcons.STROOM_STATS)
public class StroomStatsFilter extends AbstractKafkaProducerFilter {

    private final TopicNameFactory topicNameFactory;
    private StroomStatsStoreEntity stroomStatsStoreEntity;
    private String topic = null;
    private String recordKey = null;

    @Inject
    public StroomStatsFilter(final ErrorReceiverProxy errorReceiverProxy,
                             final LocationFactoryProxy locationFactory,
                             final StroomPropertyService stroomPropertyService,
                             final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                             final PathCreator pathCreator,
                             final TopicNameFactory topicNameFactory) {
        super(errorReceiverProxy, locationFactory, stroomKafkaProducerFactoryService);
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
