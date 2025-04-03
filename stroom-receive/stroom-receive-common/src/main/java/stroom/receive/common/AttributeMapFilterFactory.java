package stroom.receive.common;

import stroom.docref.DocRef;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
    private final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider;
    private final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider;
    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;

    private final CachedValue<AttributeMapFilter, ConfigState> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider,
            final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.feedNameCheckAttributeMapFilterProvider = feedNameCheckAttributeMapFilterProvider;
        this.feedStatusAttributeMapFilterProvider = feedStatusAttributeMapFilterProvider;
        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;

        // Every 60s, see if config has changed and if so create a new filter
        this.updatableAttributeMapFilter = CachedValue.builder()
                .withMaxCheckIntervalSeconds(60)
                .withStateSupplier(() -> ConfigState.fromConfig(receiveDataConfigProvider.get()))
                .withValueFunction(this::create)
                .build();
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
        filters.add(feedStatusAttributeMapFilterProvider.get());

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
