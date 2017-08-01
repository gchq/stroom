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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Component
public class InternalStatisticsImpl implements InternalStatistics {
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsImpl.class);

    private final StroomBeanStore stroomBeanStore;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;

    private volatile InternalStatistics internalStatistics = new DoNothingInternalStatistics();

    @Inject
    InternalStatisticsImpl(final StroomBeanStore stroomBeanStore,
                           final InternalStatisticDocRefCache internalStatisticDocRefCache) {
        this.stroomBeanStore = stroomBeanStore;
        this.internalStatisticDocRefCache = internalStatisticDocRefCache;
    }

    @SuppressWarnings("unused")
    @StroomStartup(priority = 100)
    public void initStatisticEventStoreBeanNames() {
        final List<String> allBeans = new ArrayList<>(
                stroomBeanStore.getStroomBeanByType(InternalStatisticsService.class));

        final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();
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

        internalStatistics = new MultiServiceInternalStatistics(internalStatisticDocRefCache, docRefTypeToServiceMap);
    }

    @Override
    public void putEvent(final InternalStatisticEvent event) {
        internalStatistics.putEvent(event);
    }

    @Override
    public void putEvents(final List<InternalStatisticEvent> events) {
        internalStatistics.putEvents(events);
    }

    /**
     * Provides protection in case client code calls create when the proper facade has not been initialised, allowing
     * the system to function albeit with the loss of the stats.
     */
    static class MultiServiceInternalStatistics implements InternalStatistics {
        private final Map<String, InternalStatisticsService> docRefTypeToServiceMap;
        private final InternalStatisticDocRefCache internalStatisticDocRefCache;

        MultiServiceInternalStatistics(final InternalStatisticDocRefCache internalStatisticDocRefCache,
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

                serviceToEventsMapMap.entrySet().forEach(entry -> {
                    //TODO as it stands if we get a failure to send with one service, we won't send to any other services
                    //it may be better to record the exception without letting it propagate and then throw an exception at
                    //the end if any failed
                    putEvents(entry.getKey(), entry.getValue());
                });
            } catch (Exception e) {
                LOGGER.warn("Error sending internal stats to all services");
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

    /**
     * Provides protection in case client code calls create when the proper facade has not been initialised, allowing
     * the system to function albeit with the loss of the stats.
     */
    private static class DoNothingInternalStatistics implements InternalStatistics {
        @Override
        public void putEvent(final InternalStatisticEvent event) {
            LOGGER.warn(
                    "putEvent called when internalStatistics has not been initialised. The statistics will not be recorded");
        }

        @Override
        public void putEvents(List<InternalStatisticEvent> events) {
            LOGGER.warn(
                    "putEvents called when internalStatistics has not been initialised. The statistics will not be recorded");
        }
    }
}
