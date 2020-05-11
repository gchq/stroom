package stroom.statistics.impl;

import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticsReceiver;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final InternalStatisticsConfig internalStatisticsConfig;

    private volatile InternalStatisticsReceiver internalStatisticsReceiver = new DoNothingInternalStatisticsReceiver();

    @Inject
    InternalStatisticsReceiverImpl(final Collection<Provider<InternalStatisticsService>> providers,
                                   final InternalStatisticsConfig internalStatisticsConfig) {
        this.providers = providers;
        this.internalStatisticsConfig = internalStatisticsConfig;
    }

    void initStatisticEventStore() {
        final Map<String, InternalStatisticsService> docRefTypeToServiceMap = new HashMap<>();
        providers.forEach(provider -> {
            final InternalStatisticsService internalStatisticsService = provider.get();
            LOGGER.debug("Registering internal statistics service for docRefType {}",
                    internalStatisticsService.getDocRefType());

            docRefTypeToServiceMap.put(
                    Preconditions.checkNotNull(internalStatisticsService.getDocRefType()),
                    internalStatisticsService);
        });

        internalStatisticsReceiver = new MultiServiceInternalStatisticsReceiver(
                docRefTypeToServiceMap,
                internalStatisticsConfig);
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
