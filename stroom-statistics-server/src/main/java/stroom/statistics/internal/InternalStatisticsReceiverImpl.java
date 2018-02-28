package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomStartup;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class InternalStatisticsReceiverImpl implements InternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsReceiverImpl.class);

    private final StroomBeanStore stroomBeanStore;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;

    private volatile InternalStatisticsReceiver internalStatisticsReceiver = new DoNothingInternalStatisticsReceiver();

    @Inject
    InternalStatisticsReceiverImpl(final StroomBeanStore stroomBeanStore,
                                   final InternalStatisticDocRefCache internalStatisticDocRefCache) {
        this.stroomBeanStore = stroomBeanStore;
        this.internalStatisticDocRefCache = internalStatisticDocRefCache;
    }

    @SuppressWarnings("unused")
    @StroomStartup(priority = 100)
    public void initStatisticEventStoreBeanNames() {
        final Map<String, InternalStatisticsService> allBeans = stroomBeanStore.getBeansOfType(InternalStatisticsService.class, false, false);

        final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();
        allBeans.forEach((name, service) -> {
            LOGGER.debug("Registering internal statistics service for docRefType {}",
                    service.getDocRefType());

            docRefTypeToServiceMap.put(
                    Preconditions.checkNotNull(service.getDocRefType()),
                    service);
        });

        internalStatisticsReceiver = new MultiServiceInternalStatisticsReceiver(internalStatisticDocRefCache, docRefTypeToServiceMap);
    }

    @Override
    public void putEvent(final InternalStatisticEvent event) {
        internalStatisticsReceiver.putEvent(event);
    }

    @Override
    public void putEvents(final List<InternalStatisticEvent> events) {
        internalStatisticsReceiver.putEvents(events);
    }

}
