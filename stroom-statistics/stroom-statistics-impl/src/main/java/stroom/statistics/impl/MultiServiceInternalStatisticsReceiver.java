package stroom.statistics.impl;

import stroom.docref.DocRef;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Passes the internal statistic events to zero-many {@link InternalStatisticsService} instances
 * as defined by the docRefTypeToServiceMap
 */
class MultiServiceInternalStatisticsReceiver implements InternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiServiceInternalStatisticsReceiver.class);

    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;
    private final InternalStatisticsConfig internalStatisticsConfig;

    MultiServiceInternalStatisticsReceiver(final Map<String, InternalStatisticsService> docRefTypeToServiceMap,
                                           final InternalStatisticsConfig internalStatisticsConfig) {

        this.docRefTypeToServiceMap = Objects.requireNonNull(docRefTypeToServiceMap);
        this.internalStatisticsConfig = Objects.requireNonNull(internalStatisticsConfig);
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
                                    internalStatisticsConfig.getEnabledDocRefs(event.getKey())
                                            .stream()
                                            .map(docRef ->
                                                    Tuple.of(getServiceForType(docRef.getType()),
                                                            docRef,
                                                            event))
                                            .filter(serviceDocRefEventTuple3 ->
                                                    serviceDocRefEventTuple3._1() != null)) // ignore ones with no svc
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
            //An exception with one service will be logged and swallowed
            serviceToEventsMapMap.forEach(this::putEvents);

        } catch (final RuntimeException e) {
            LOGGER.error("Error sending internal stats", e);
        }
    }

    private InternalStatisticsService getServiceForType(final String type) {
        InternalStatisticsService service = docRefTypeToServiceMap.get(type);
        if (service == null) {
            LOGGER.warn("No InternalStatisticsService for type {}", type);
        }
        return service;
    }

    private void putEvents(final InternalStatisticsService service,
                           final Map<DocRef, List<InternalStatisticEvent>> eventsMap) {
        try {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Putting events for docRef(s) [{}] to {}",
                        eventsMap.keySet().stream()
                                .map(DocRef::getName)
                                .collect(Collectors.joining(",")),
                        service.getClass().getName());
            }

            service.putEvents(eventsMap);
        } catch (final RuntimeException e) {
            final String baseMsg = "Error sending internal statistics to {}";
            final String serviceClassName = service.getClass().getSimpleName();
            final Throwable cause = e.getCause();
            LOGGER.error(
                    baseMsg + ", due to: [{}] caused by: [{}] (enable DEBUG for full stacktrace)",
                    serviceClassName,
                    e.getMessage(),
                    cause != null ? cause.getMessage() : "unknown");
            LOGGER.debug(baseMsg, serviceClassName, e);
        }
    }
}
