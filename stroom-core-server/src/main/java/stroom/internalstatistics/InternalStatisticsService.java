package stroom.internalstatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.statistics.common.StatisticEvent;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A service for recording internal statistics on the health of Stroom
 */
public interface InternalStatisticsService {

//    WebSequenceDiagram
//    IntStatSrv -> DataSrcProvReg: getDataSrcProv(dsName)
//    DataSrcProvReg -> SrvDisco: getDataSrcProv
//    IntStatSrv -> IntStatSrv: flatMap
//    note right of IntStatSrv
//    flat map events to (event, datasource) pair
//    end note
//    IntStatSrv->IntStatConsumerFact: getConsumer
//    IntStatSrv -> IntStatConsumer: putEvents(statEvents, datasource)

    Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsService.class);

    Consumer<Throwable> DEFAULT_EXCEPTION_HANDLER = throwable -> {
        LOGGER.error("Swallowing exception from putting internal statistic events", throwable);
    };

    /**
     * @param statisticEvent A statistic event to record.
     *                        For the statistic event to be record by an implementing service there must be
     *                        All exceptions will be swallowed and logged as errors
     */
    default void putEvent(StatisticEvent statisticEvent) {
        putEvents(Collections.singletonList(statisticEvent));
    }

    default void putEvent(final StatisticEvent statisticEvent, final Consumer<Throwable> exceptionHandler) {
        putEvents(Collections.singletonList(statisticEvent), exceptionHandler);
    }

    default void putEvents(List<StatisticEvent> statisticEvents, final Consumer<Throwable> exceptionHandler) {
        try {
            putEvents(statisticEvents);
        } catch (Throwable e) {
            DEFAULT_EXCEPTION_HANDLER.accept(e);
        }
    }

    /**
     * @param statisticEvents A list of statistic events for one..many statistic names to record.
     *                        All exceptions will be swallowed and logged as errors
     */
    void putEvents(List<StatisticEvent> statisticEvents);


    String getName();
}
