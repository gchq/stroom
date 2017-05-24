package stroom.statistics.sql;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.internalstatistics.DecoratedInternalStatisticEvent;
import stroom.internalstatistics.InternalStatisticsService;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SQLInternalStatisticsService implements InternalStatisticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SQLInternalStatisticsService.class);

    private static final String PROP_KEY_DOC_REF_TYPE = "stroom.services.sqlStatistics.docRefType";

    private final StroomPropertyService stroomPropertyService;
    private final Statistics statisticsService;
    private final String docRefType;

    @Inject
    public SQLInternalStatisticsService(final StroomPropertyService stroomPropertyService,
                                        final Statistics statisticsService) {
        this.stroomPropertyService = stroomPropertyService;
        this.statisticsService = statisticsService;
        this.docRefType = stroomPropertyService.getProperty(PROP_KEY_DOC_REF_TYPE);
    }

    @Override
    public void putEvents(final List<DecoratedInternalStatisticEvent> internalStatisticEvents) {

        List<StatisticEvent> statisticEvents = Preconditions.checkNotNull(internalStatisticEvents).stream()
                .map(this::internalEventMapper)
                .collect(Collectors.toList());

        statisticsService.putEvents(statisticEvents);
    }

    private StatisticEvent internalEventMapper(final DecoratedInternalStatisticEvent internalStatisticEvent) {

        Preconditions.checkNotNull(internalStatisticEvent);
        switch (internalStatisticEvent.getType()) {
            case COUNT:
                return mapCountEvent(internalStatisticEvent);
            case VALUE:
                return mapValueEvent(internalStatisticEvent);
            default:
                throw new IllegalArgumentException("Unknown type: " + internalStatisticEvent.getType());
        }
    }

    private StatisticEvent mapCountEvent(final DecoratedInternalStatisticEvent internalStatisticEvent) {
        return StatisticEvent.createCount(
                internalStatisticEvent.getTimeMs(),
                internalStatisticEvent.getDocRef().getName(),
                internalStatisticEvent.getTags().entrySet().stream()
                        .map(entry -> new StatisticTag(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()),
                internalStatisticEvent.getValueAsLong());
    }

    private StatisticEvent mapValueEvent(final DecoratedInternalStatisticEvent internalStatisticEvent) {
        return StatisticEvent.createValue(
                internalStatisticEvent.getTimeMs(),
                internalStatisticEvent.getDocRef().getName(),
                internalStatisticEvent.getTags().entrySet().stream()
                    .map(entry -> new StatisticTag(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList()),
                internalStatisticEvent.getValueAsDouble());
    }

    @Override
    public String getDocRefType() {
        return docRefType;
    }
}
