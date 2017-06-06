package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import io.vavr.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.query.api.v1.DocRef;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class InternalStatisticsFacadeFactoryImpl implements InternalStatisticsFacadeFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsFacadeFactoryImpl.class);

    private static final DoNothingInternalStatisticsFacade DO_NOTHING_FACADE = new DoNothingInternalStatisticsFacade();

    private final StroomBeanStore stroomBeanStore;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;
    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();

    private InternalStatisticsFacade internalStatisticsFacade;

    @Inject
    public InternalStatisticsFacadeFactoryImpl(final StroomBeanStore stroomBeanStore,
                                               final InternalStatisticDocRefCache internalStatisticDocRefCache) {

        this.stroomBeanStore = stroomBeanStore;
        this.internalStatisticDocRefCache = internalStatisticDocRefCache;
    }

    @SuppressWarnings("unused")
    @StroomStartup(priority = 100)
    public void initStatisticEventStoreBeanNames() {
        final List<String> allBeans = new ArrayList<>(
                stroomBeanStore.getStroomBeanByType(InternalStatisticsService.class));
        for (final String beanName : allBeans) {
            final InternalStatisticsService internalStatisticsService = (InternalStatisticsService) stroomBeanStore.getBean(beanName);

            // only add stores that are spring beans and are enabled
            if (internalStatisticsService != null) {
                LOGGER.debug("Registering internal statistics service for docRefType {}",
                        internalStatisticsService.getDocRefType());

                docRefTypeToServiceMap.put(
                        Preconditions.checkNotNull(internalStatisticsService.getDocRefType()),
                        internalStatisticsService);
            }
        }

        internalStatisticsFacade = new MultiServiceInternalStatisticsFacade(internalStatisticDocRefCache, docRefTypeToServiceMap);
    }

    /**
     * @return An instance of the facade for recording internal statistics.  Do not hold on to the returned instance.
     * Call create, use it then throw it away.
     */
    @Override
    public InternalStatisticsFacade create() {
        if (internalStatisticsFacade == null) {
            return DO_NOTHING_FACADE;
        }
        return internalStatisticsFacade;
    }

    /**
     * Provides protection in case client code calls create when the proper facade has not been initialised, allowing
     * the system to function albeit with the loss of the stats.
     */
    private static class DoNothingInternalStatisticsFacade implements InternalStatisticsFacade {

        @Override
        public void putEvents(List<InternalStatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {
            InternalStatisticsFacade.LOGGER.warn(
                    "putEvents called when internalStatisticsFacade has not been initialised. The statistics will not be recorded");

        }
    }

    private static class MultiServiceInternalStatisticsFacade implements InternalStatisticsFacade {

        private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;
        private final InternalStatisticDocRefCache internalStatisticDocRefCache;

        MultiServiceInternalStatisticsFacade(final InternalStatisticDocRefCache internalStatisticDocRefCache,
                                     final Map<String, InternalStatisticsService> docRefTypeToServiceMap) {

            this.docRefTypeToServiceMap = Preconditions.checkNotNull(docRefTypeToServiceMap);
            this.internalStatisticDocRefCache = Preconditions.checkNotNull(internalStatisticDocRefCache);
        }

        @Override
        public void putEvents(List<InternalStatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {

            //Group the events by service and docref
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

            //fire off the list of internal stats events to each service asynchronously
            //ensuring all are completed
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
}
