package stroom.internalstatistics;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v1.DocRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class InternalStatisticsFacade {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsFacade.class);

    private static final Consumer<Throwable> LOG_ONLY_EXCEPTION_HANDLER = throwable -> {
        LOGGER.error("Swallowing exception from putting internal statistic events", throwable);
    };

    private final Map<String, List<DocRef>> keyToDocRefsMap;
    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;

    InternalStatisticsFacade(final Map<String, List<DocRef>> keyToDocRefsMap,
                             final Map<String, InternalStatisticsService> docRefTypeToServiceMap) {

        this.keyToDocRefsMap = Preconditions.checkNotNull(keyToDocRefsMap);
        this.docRefTypeToServiceMap = Preconditions.checkNotNull(docRefTypeToServiceMap);
    }

    /**
     * @param internalStatisticEvent A statistic event to record.
     *                        For the statistic event to be record by an implementing service there must be
     *                        All exceptions will be swallowed and logged as errors
     */
    void putEvent(InternalStatisticEvent internalStatisticEvent) {
        putEvents(Collections.singletonList(internalStatisticEvent), LOG_ONLY_EXCEPTION_HANDLER);
    }

    void putEvent(final InternalStatisticEvent internalStatisticEvent, final Consumer<Throwable> exceptionHandler) {
        putEvents(Collections.singletonList(internalStatisticEvent), exceptionHandler);
    }

//    void putEvents(List<InternalStatisticEvent> statisticEvents, final Consumer<Throwable> exceptionHandler) {
//        try {
//            putEvents(statisticEvents);
//        } catch (Throwable e) {
//            exceptionHandler.accept(e);
//        }
//    }

    public void putEvents(List<InternalStatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {

        List<InternalStatisticsService> applicableServices = statisticEvents.stream()
                .map(InternalStatisticEvent::getKey)
                .

        Consumer<Throwable> rethrowExceptionHandler = throwable -> {
            throw new RuntimeException(throwable);
        };

        docRefTypeToServiceMap.entrySet().stream()
                .collect(Collectors.groupingBy(Entry::))

        //fire off the list of internal stats events to each service asynchronously
        //ensuring
        CompletableFuture<Void>[] futures = docRefTypeToServiceMap.entrySet().stream()
                .map(service -> CompletableFuture.runAsync(() ->
                        service.putEvents(statisticEvents, rethrowExceptionHandler)))
                .toArray(len -> new CompletableFuture[len]);

        CompletableFuture.allOf(futures)
                .exceptionally(throwable -> {
                    exceptionHandler.accept(throwable);
                    return null;
                });
    }

    private List<DecoratedInternalStatisticEvent> decorateEvents(List<InternalStatisticEvent> events) {

    }

}
