package stroom.internalstatistics;

import com.google.common.base.Preconditions;
import io.vavr.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.api.v1.DocRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class InternalStatisticsFacadeImpl implements InternalStatisticsFacade {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsFacadeImpl.class);

    private static final Consumer<Throwable> LOG_ONLY_EXCEPTION_HANDLER = throwable ->
            LOGGER.error("Swallowing exception from putting internal statistic events", throwable);

    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;

    InternalStatisticsFacadeImpl(final InternalStatisticDocRefCache internalStatisticDocRefCache,
                                 final Map<String, InternalStatisticsService> docRefTypeToServiceMap) {

        this.docRefTypeToServiceMap = Preconditions.checkNotNull(docRefTypeToServiceMap);
        this.internalStatisticDocRefCache = Preconditions.checkNotNull(internalStatisticDocRefCache);
    }



    @Override
    public void putEvents(List<InternalStatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {

        Map<InternalStatisticsService, Map<DocRef, List<InternalStatisticEvent>>> serviceToEventsMapMap = statisticEvents.stream()
                .flatMap(event ->
                        internalStatisticDocRefCache.getDocRefs(event.getKey()).stream()
                                .map(docRef -> new Tuple3<>(docRefTypeToServiceMap.get(docRef.getType()), docRef, event))
                                .filter(tuple3 -> tuple3._1() != null))
                .collect(Collectors.groupingBy(
                        Tuple3::_1, //service
                        Collectors.groupingBy(
                                Tuple3::_2, //docRef
                                Collectors.mapping(
                                        Tuple3::_3, //event
                                        Collectors.toList()))));


        Consumer<Throwable> rethrowExceptionHandler = throwable -> {
            throw new RuntimeException(throwable);
        };

        //fire off the list of internal stats events to each service asynchronously
        //ensuring
        CompletableFuture<Void>[] futures = serviceToEventsMapMap.entrySet().stream()
                .map(entry ->
                        CompletableFuture.runAsync(() -> {
                            InternalStatisticsService service = entry.getKey();
                            service.putEvents(entry.getValue());
                        }))
                .toArray(len -> new CompletableFuture[len]);

        CompletableFuture.allOf(futures)
                .exceptionally(throwable -> {
                    exceptionHandler.accept(throwable);
                    return null;
                });
    }

}
