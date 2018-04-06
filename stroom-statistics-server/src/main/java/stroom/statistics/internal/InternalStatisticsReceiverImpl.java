package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.lifecycle.StroomStartup;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@SuppressWarnings("unused")
public class InternalStatisticsReceiverImpl implements InternalStatisticsReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticsReceiverImpl.class);

    private final Collection<Provider<InternalStatisticsService>> providers;
    private final InternalStatisticDocRefCache internalStatisticDocRefCache;

    private volatile InternalStatisticsReceiver internalStatisticsReceiver = new DoNothingInternalStatisticsReceiver();

    @Inject
    InternalStatisticsReceiverImpl(final Collection<Provider<InternalStatisticsService>> providers,
                                   final InternalStatisticDocRefCache internalStatisticDocRefCache) {
        this.providers = providers;
        this.internalStatisticDocRefCache = internalStatisticDocRefCache;
    }

    @SuppressWarnings("unused")
    @StroomStartup(priority = 100)
    public void initStatisticEventStoreBeanNames() {
        final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();
        providers.forEach(provider -> {
            final InternalStatisticsService internalStatisticsService = provider.get();
            LOGGER.debug("Registering internal statistics service for docRefType {}",
                    internalStatisticsService.getDocRefType());

            docRefTypeToServiceMap.put(
                    Preconditions.checkNotNull(internalStatisticsService.getDocRefType()),
                    internalStatisticsService);
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
