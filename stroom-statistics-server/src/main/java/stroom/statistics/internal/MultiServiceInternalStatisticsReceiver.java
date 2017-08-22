package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import io.vavr.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.api.v2.DocRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Passes the internal statistic events to zero-many {@link InternalStatisticsService} instances
 * as defined by the docRefTypeToServiceMap
 */
class MultiServiceInternalStatisticsReceiver implements InternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiServiceInternalStatisticsReceiver.class);

    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;

    MultiServiceInternalStatisticsReceiver(final InternalStatisticDocRefCache internalStatisticDocRefCache,
                                           final Map<String, InternalStatisticsService> docRefTypeToServiceMap) {

        this.docRefTypeToServiceMap = Preconditions.checkNotNull(docRefTypeToServiceMap);
        this.internalStatisticDocRefCache = Preconditions.checkNotNull(internalStatisticDocRefCache);
    }

    @Override
    public void putEvent(final InternalStatisticEvent event) {
        putEvents(Collections.singletonList(event));
    }

    @Override
    public void putEvents(final List<InternalStatisticEvent> statisticEvents) {
        try {
            // Group the events by service and docref
            Map<InternalStatisticsService, Map<DocRef, List<InternalStatisticEvent>>> serviceToEventsMapMap =
                    statisticEvents.stream()
                            .flatMap(event ->
                                    internalStatisticDocRefCache.getDocRefs(event.getKey()).stream()
                                            .map(docRef ->
                                                    new Tuple3<>(docRefTypeToServiceMap.get(docRef.getType()),
                                                            docRef,
                                                            event))
                                            .filter(tuple3 -> tuple3._1() != null))
                            .collect(Collectors.groupingBy(
                                    Tuple3::_1, //service
                                    Collectors.groupingBy(
                                            Tuple3::_2, //docRef
                                            Collectors.mapping(
                                                    Tuple3::_3, //event
                                                    Collectors.toList()))));

            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Putting {} events to {} services",
                        statisticEvents.size(),
                        serviceToEventsMapMap.size());
            }
            serviceToEventsMapMap.entrySet().forEach(entry -> {
                //TODO as it stands if we get a failure to send with one service, we won't send to any other services
                //it may be better to record the exception without letting it propagate and then throw an exception at
                //the end if any failed
                putEvents(entry.getKey(), entry.getValue());
            });
        } catch (Exception e) {
            Throwable rootCause = Throwables.getRootCause(e);
            LOGGER.warn("Error sending internal stats to all services [{}]", rootCause.getMessage());
        }
    }

    private void putEvents(final InternalStatisticsService service, Map<DocRef, List<InternalStatisticEvent>> eventsMap) {
        try {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Putting events for docRef(s) [{}] to {}",
                        eventsMap.keySet().stream()
                                .map(DocRef::getName)
                                .collect(Collectors.joining(",")),
                        service.getClass().getName());
            }

            service.putEvents(eventsMap);
        } catch (Exception e) {
            throw new RuntimeException("Error sending internal statistics to service of type " + service.getDocRefType(), e);
        }
    }
}
