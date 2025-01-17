package stroom.receive.common;

import stroom.docref.DocRef;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.NullSafe;
import stroom.util.concurrent.PeriodicallyUpdatedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a chain of filters for checking and possibly adding to the contents
 * of the {@link stroom.meta.api.AttributeMap}
 */
@Singleton
public class AttributeMapFilterFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapFilterFactory.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<FeedStatusService> feedStatusServiceProvider;
    private final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider;
    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;

    private final PeriodicallyUpdatedValue<AttributeMapFilter, ConfigState> updatableAttributeMapFilter;

    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<FeedStatusService> feedStatusServiceProvider,
            final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.feedStatusServiceProvider = feedStatusServiceProvider;
        this.feedNameCheckAttributeMapFilterProvider = feedNameCheckAttributeMapFilterProvider;
        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;

        // Every 60s, see if config has changed and if so create a new filter
        this.updatableAttributeMapFilter = new PeriodicallyUpdatedValue<>(
                Duration.ofSeconds(60),
                this::create,
                () -> ConfigState.fromConfig(receiveDataConfigProvider.get()));
    }

    private AttributeMapFilter create(final ConfigState configState) {
        final List<AttributeMapFilter> filters = new ArrayList<>();

        // This one adds the feed attr if it is not there (and config allows
        // so needs to go before most others.
        filters.add(feedNameCheckAttributeMapFilterProvider.get());

        if (NullSafe.isNonBlankString(configState.receiptPolicyUuid)) {
            LOGGER.info("Using data receipt policy to filter received data");
            filters.add(dataReceiptPolicyAttributeMapFilterFactory.create(
                    new DocRef(ReceiveDataRules.TYPE, configState.receiptPolicyUuid)));
        }

        // The feed status filter will determine if the feed status check needs
        // to happen as the config for it is different between proxy and stroom.
        // Proxy and stroom each have a different FeedStatusService impl bound.
        filters.add(new FeedStatusAttributeMapFilter(feedStatusServiceProvider.get()));

        return AttributeMapFilter.wrap(filters);
    }

    public AttributeMapFilter create() {
        return updatableAttributeMapFilter.getValue();
    }

    // --------------------------------------------------------------------------------


    private record ConfigState(
            String receiptPolicyUuid) {

        public static ConfigState fromConfig(
                final ReceiveDataConfig receiveDataConfig) {

            return new ConfigState(receiveDataConfig.getReceiptPolicyUuid());
        }
    }
}
