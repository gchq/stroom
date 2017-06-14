package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import io.vavr.Tuple3;
import stroom.query.api.v1.DocRef;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class MultiServiceInternalStatisticsFacade implements InternalStatisticsFacade {

    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;

    MultiServiceInternalStatisticsFacade(final InternalStatisticDocRefCache internalStatisticDocRefCache,
                                         final Map<String, InternalStatisticsService> docRefTypeToServiceMap) {

        this.docRefTypeToServiceMap = Preconditions.checkNotNull(docRefTypeToServiceMap);
        this.internalStatisticDocRefCache = Preconditions.checkNotNull(internalStatisticDocRefCache);
    }

    @Override
    public void putEvents(List<InternalStatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {

        try {
            //Group the events by service and docref
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

            serviceToEventsMapMap.entrySet().forEach(entry -> {
                //TODO as it stands if we get a failure to send with one service, we won't send to any other services
                //it may be better to record the exception without letting it propogate and then throw an exception at
                //the end if any failed
                putEvents(entry.getKey(), entry.getValue());
            });
        } catch (Exception e) {
            LOGGER.error("Error sending internal stats to all services", e);
            exceptionHandler.accept(e);
        }
    }

    private void putEvents(final InternalStatisticsService service, Map<DocRef, List<InternalStatisticEvent>> eventsMap) {
        try {
            service.putEvents(eventsMap);
        } catch (Exception e) {
            throw new RuntimeException("Error sending internal statistics to service of type " + service.getDocRefType(), e);
        }
    }
}
