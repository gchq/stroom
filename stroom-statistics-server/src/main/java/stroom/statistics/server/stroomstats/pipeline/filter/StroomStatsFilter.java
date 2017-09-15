package stroom.statistics.server.stroomstats.pipeline.filter;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.kafka.StroomKafkaProducerFactoryService;
import stroom.connectors.kafka.filter.AbstractKafkaProducerFilter;
import stroom.node.server.StroomPropertyService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.writer.PathCreator;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.stats.shared.StroomStatsStoreEntity;
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

    private StroomStatsStoreEntity stroomStatsStoreEntity;

    @Inject
    public StroomStatsFilter(final ErrorReceiverProxy errorReceiverProxy,
                             final LocationFactoryProxy locationFactory,
                             final StroomPropertyService stroomPropertyService,
                             final StroomKafkaProducerFactoryService stroomKafkaProducerFactoryService,
                             final PathCreator pathCreator) {
        super(errorReceiverProxy, locationFactory, stroomKafkaProducerFactoryService);
    }

    @Override
    public String getTopic() {
        return null;
    }

    @Override
    public String getRecordKey() {
        return null;
    }

    @PipelineProperty(description = "The stroom-stats data source to record statistics against.")
    public void setStatisticsDataSource(final StroomStatsStoreEntity stroomStatsStoreEntity) {
        this.stroomStatsStoreEntity = stroomStatsStoreEntity;
    }
}
