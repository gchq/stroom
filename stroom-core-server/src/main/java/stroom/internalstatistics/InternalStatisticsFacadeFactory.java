package stroom.internalstatistics;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class InternalStatisticsFacadeFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsFacadeFactory.class);

    private static final DoNothingInternalStatisticsFacade DO_NOTHING_FACADE = new DoNothingInternalStatisticsFacade();

    private final StroomBeanStore stroomBeanStore;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;
    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();

    private InternalStatisticsFacadeImpl internalStatisticsFacade;

    @Inject
    public InternalStatisticsFacadeFactory(final StroomBeanStore stroomBeanStore,
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

        internalStatisticsFacade = new InternalStatisticsFacadeImpl(internalStatisticDocRefCache, docRefTypeToServiceMap);
    }

    /**
     * @return An instance of the facade for recording internal statistics.  Do not hold on to the returned instance.
     * Call create, use it then throw it away.
     */
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
}
