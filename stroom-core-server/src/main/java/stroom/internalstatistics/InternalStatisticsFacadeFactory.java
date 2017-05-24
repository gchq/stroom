package stroom.internalstatistics;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v1.DocRef;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class InternalStatisticsFacadeFactory implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsFacadeFactory.class);

    private static final String STROOM_STATISTIC_ENGINES_PROPERTY_NAME = "stroom.statistics.common.statisticEngines";

    private final StroomBeanStore stroomBeanStore;
    private final StroomPropertyService stroomPropertyService;
    private final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();
    private final Map<String, List<DocRef>> keyToDocRefsMap = new HashMap<>();

    public InternalStatisticsFacadeFactory(final StroomBeanStore stroomBeanStore,
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
            if (internalStatisticsService != null) {
                docRefTypeToServiceMap.put(Preconditions.checkNotNull(internalStatisticsService.getDocRefType()), internalStatisticsService);
            }
        }
    }

    public InternalStatisticsFacade create() {
        return new InternalStatisticsFacade(stroomPropertyService, docRefTypeToServiceMap);
    }


//    public static class NoProvidersInternalStatisticsService implements InternalStatisticsService {
//
//        private static final Logger LOGGER = LoggerFactory.getLogger(NoProvidersInternalStatisticsService.class);
//
//        @Override
//        public void putEvents(final List<StatisticEvent> statisticEvents) {
//            LOGGER.debug("");
//
//        }
//
//        @Override
//        public String getDocRefType() {
//            throw new UnsupportedOperationException("getName is not supported on NoProvidersInternalStatisticsService");
//        }
//    }


//    public boolean isDataStoreEnabled(final String engineName) {
//        final String enabledEngines = stroomPropertyService.getProperty(STROOM_STATISTIC_ENGINES_PROPERTY_NAME);
//
//        LOGGER.debug("{} property value: {}", STROOM_STATISTIC_ENGINES_PROPERTY_NAME, enabledEngines);
//
//        boolean result = false;
//
//        if (enabledEngines != null) {
//            for (final String engine : enabledEngines.split(",")) {
//                if (engine.equals(engineName)) {
//                    result = true;
//                }
//            }
//        }
//        return result;
//    }

//    private String toKey(final String name) {
//        return name.trim().toUpperCase();
//    }
}
