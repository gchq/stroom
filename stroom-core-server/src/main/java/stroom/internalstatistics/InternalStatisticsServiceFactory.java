package stroom.internalstatistics;

import org.springframework.stereotype.Component;
import stroom.statistics.common.StatisticEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class InternalStatisticsServiceFactory {


//    private final KafkaInternalStatisticsService kafkaInternalStatisticsService;
//    private final SQLInternalStatisticsService sqlInternalStatisticsService;
//
//    @Inject
//    public InternalStatisticsServiceFactory(final KafkaInternalStatisticsService kafkaInternalStatisticsService,
//                                            final SQLInternalStatisticsService sqlInternalStatisticsService) {
//        this.kafkaInternalStatisticsService = kafkaInternalStatisticsService;
//        this.sqlInternalStatisticsService = sqlInternalStatisticsService;
//    }

//    public InternalStatisticsService create() {
//
//    }

    public static class MultiProviderInternalStatisticsService implements InternalStatisticsService {

        private final List<InternalStatisticsService> services;

        public MultiProviderInternalStatisticsService(List<InternalStatisticsService> services) {
            this.services = new ArrayList<>(services);
        }

        @Override
        public void putEvents(List<StatisticEvent> statisticEvents, Consumer<Throwable> exceptionHandler) {

            Consumer<Throwable> throwExceptionHandler = throwable -> {
                throw new RuntimeException(throwable);
            };

            //fire off the list of internal stats events to each service asynchronously
            //ensuring
            CompletableFuture<Void>[] futures = services.stream()
                    .map(service -> CompletableFuture.runAsync(() ->
                            service.putEvents(statisticEvents, throwExceptionHandler)))
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
            return null;
        }
    }
}
