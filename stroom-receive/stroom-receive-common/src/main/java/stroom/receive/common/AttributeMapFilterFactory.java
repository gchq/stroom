package stroom.receive.common;

import stroom.receive.rules.shared.ReceiveAction;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a chain of filters for checking and possibly adding to the contents
 * of the {@link stroom.meta.api.AttributeMap}
 */
@Singleton
public class AttributeMapFilterFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapFilterFactory.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final Provider<StreamTypeValidator> streamTypeValidatorProvider;
    private final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider;
    private final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider;
    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;

    private final CachedValue<AttributeMapFilter, Void> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<StreamTypeValidator> streamTypeValidatorProvider,
            final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider,
            final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.streamTypeValidatorProvider = streamTypeValidatorProvider;
        this.feedNameCheckAttributeMapFilterProvider = feedNameCheckAttributeMapFilterProvider;
        this.feedStatusAttributeMapFilterProvider = feedStatusAttributeMapFilterProvider;
        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;

        // Every 60s, create a new filter
        this.updatableAttributeMapFilter = CachedValue.builder()
                .withMaxCheckIntervalSeconds(60)
                .withoutStateSupplier()
                .withValueSupplier(this::doCreate)
                .build();

        // Ensure it is initialised
        updatableAttributeMapFilter.getValue();
    }

    private AttributeMapFilter doCreate() {
        final List<AttributeMapFilter> filters = new ArrayList<>();
        final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

        // Validate the stream type, if there is one
        filters.add(streamTypeValidatorProvider.get());

        // This one validates the feed attr, or if it is not there (and config allows)
        // generates it, so needs to go before most others.
        filters.add(feedNameCheckAttributeMapFilterProvider.get());

        switch (receiveDataConfig.getReceiptCheckMode()) {
            case FEED_STATUS -> {
                LOGGER.debug("Using feed status checks to filter received data");
                // The feed status filter will determine if the feed status check needs
                // to happen as the config for it is different between proxy and stroom.
                // Proxy and stroom each have a different FeedStatusService impl bound.
                filters.add(feedStatusAttributeMapFilterProvider.get());
            }
            case RECEIPT_POLICY -> {
                LOGGER.debug("Using data receipt policy to filter received data");
                filters.add(dataReceiptPolicyAttributeMapFilterFactory.create());
            }
            case NONE -> {
                final AttributeMapFilter fallbackFilter = createFallbackFilter(
                        receiveDataConfig.getFallbackReceiveAction());
                filters.add(fallbackFilter);
                LOGGER.debug(() -> LogUtil.message("No data receipt check, using fallbackFilter {}",
                        fallbackFilter.getClass().getSimpleName()));
            }
        }

        // This copes with nulls
        return AttributeMapFilter.wrap(filters);
    }

    private AttributeMapFilter createFallbackFilter(final ReceiveAction fallbackReceiveAction) {
        final ReceiveAction receiveAction = Objects.requireNonNullElse(
                fallbackReceiveAction,
                ReceiveDataConfig.DEFAULT_FALLBACK_RECEIVE_ACTION);
        return switch (receiveAction) {
            case RECEIVE -> ReceiveAllAttributeMapFilter.getInstance();
            case REJECT -> RejectAllAttributeMapFilter.getInstance();
            case DROP -> DropAllAttributeMapFilter.getInstance();
        };
    }

    public AttributeMapFilter create() {
        // Async so we don't hold up receipt while waiting for a response to come
        // back from downstream and the filters to be constructed.
        return updatableAttributeMapFilter.getValueAsync();
    }
}
