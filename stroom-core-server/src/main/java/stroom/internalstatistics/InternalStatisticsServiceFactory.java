package stroom.internalstatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.common.StatisticEvent;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Component
public class InternalStatisticsServiceFactory implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsServiceFactory.class);

    private static final String STROOM_STATISTIC_ENGINES_PROPERTY_NAME = "stroom.statistics.common.statisticEngines";

    private static InternalStatisticsService NO_PROVIDERS_SERVICE = new NoProvidersInternalStatisticsService();


    private final StroomBeanStore stroomBeanStore;
    private final StroomPropertyService stroomPropertyService;
    private final List<InternalStatisticsService> serviceList = new ArrayList<>();

    public InternalStatisticsServiceFactory(final StroomBeanStore stroomBeanStore,
                                            final StroomPropertyService stroomPropertyService) {
        this.stroomBeanStore = stroomBeanStore;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initStatisticEventStoreBeanNames();
    }

    @StroomStartup(priority = 100)
    public void initStatisticEventStoreBeanNames() {
        final List<String> allBeans = new ArrayList<>(stroomBeanStore.getStroomBeanByType(InternalStatisticsService.class));
        for (final String beanName : allBeans) {
            final InternalStatisticsService internalStatisticsService = (InternalStatisticsService) stroomBeanStore.getBean(beanName);

            // only add stores that are spring beans and are enabled
            if (internalStatisticsService != null && isDataStoreEnabled(internalStatisticsService.getName())) {
                serviceList.add(internalStatisticsService);
            }
        }
    }

    public InternalStatisticsService create() {

        if (serviceList.isEmpty()) {
            return NO_PROVIDERS_SERVICE;
        } else if (serviceList.size() == 1) {
            return serviceList.get(0);
        } else {
            return new MultiProviderInternalStatisticsService(serviceList);
        }
    }


    public static class NoProvidersInternalStatisticsService implements InternalStatisticsService {

        private static final Logger LOGGER = LoggerFactory.getLogger(NoProvidersInternalStatisticsService.class);

        @Override
        public void putEvents(final List<StatisticEvent> statisticEvents) {
            LOGGER.debug("");

        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("getName is not supported on NoProvidersInternalStatisticsService");
        }
    }


    public static class MultiProviderInternalStatisticsService implements InternalStatisticsService {

        private final List<InternalStatisticsService> services;

        public MultiProviderInternalStatisticsService(Collection<InternalStatisticsService> services) {
            this.services = new ArrayList<>(services);
        }

        @Override
        public void putEvents(List<StatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {

            Consumer<Throwable> rethrowExceptionHandler = throwable -> {
                throw new RuntimeException(throwable);
            };

            //fire off the list of internal stats events to each service asynchronously
            //ensuring
            CompletableFuture<Void>[] futures = services.stream()
                    .map(service -> CompletableFuture.runAsync(() ->
                            service.putEvents(statisticEvents, rethrowExceptionHandler)))
                    .toArray(len -> new CompletableFuture[len]);

            CompletableFuture.allOf(futures)
                    .exceptionally(throwable -> {
                        exceptionHandler.accept(throwable);
                        return null;
                    });
        }

        /**
         * @param statisticEvents A list of statistic events for one..many statistic names to record.
         *                        All exceptions will be swallowed and logged as errors
         */
        @Override
        public void putEvents(List<StatisticEvent> statisticEvents) {

            putEvents(statisticEvents, InternalStatisticsService.LOG_ONLY_EXCEPTION_HANDLER);
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("getName is not supported on MultiProviderInternalStatisticsService");
        }
    }

    public boolean isDataStoreEnabled(final String engineName) {
        final String enabledEngines = stroomPropertyService.getProperty(STROOM_STATISTIC_ENGINES_PROPERTY_NAME);

        LOGGER.debug("{} property value: {}", STROOM_STATISTIC_ENGINES_PROPERTY_NAME, enabledEngines);

        boolean result = false;

        if (enabledEngines != null) {
            for (final String engine : enabledEngines.split(",")) {
                if (engine.equals(engineName)) {
                    result = true;
                }
            }
        }
        return result;
    }

    private String toKey(final String name) {
        return name.trim().toUpperCase();
    }
}
