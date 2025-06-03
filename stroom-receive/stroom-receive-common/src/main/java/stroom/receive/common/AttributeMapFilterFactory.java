package stroom.receive.common;

import stroom.receive.common.ReceiveDataConfig.ReceiptCheckMode;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.security.api.CommonSecurityContext;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
    private final CommonSecurityContext securityContext;
    private final ContentAutoCreationAttrMapFilterFactory contentAutoCreationAttrMapFilterFactory;
    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;

    private final CachedValue<AttributeMapFilter, Void> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<StreamTypeValidator> streamTypeValidatorProvider,
            final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider,
            final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider,
            final CommonSecurityContext securityContext,
            final ContentAutoCreationAttrMapFilterFactory contentAutoCreationAttrMapFilterFactory,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.streamTypeValidatorProvider = streamTypeValidatorProvider;
        this.feedNameCheckAttributeMapFilterProvider = feedNameCheckAttributeMapFilterProvider;
        this.feedStatusAttributeMapFilterProvider = feedStatusAttributeMapFilterProvider;
        this.securityContext = securityContext;
        this.contentAutoCreationAttrMapFilterFactory = contentAutoCreationAttrMapFilterFactory;
        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;

        // Every 60s, create a new filter.
        // Some of the filters involve calls to a downstream stroom/proxy, so
        // we have no state to check to avoid the update every 60s.
        this.updatableAttributeMapFilter = CachedValue.builder()
                .withMaxCheckIntervalSeconds(60)
                .withoutStateSupplier()
                .withValueSupplier(this::doCreate)
                .build();

        // Ensure it is initialised
        updatableAttributeMapFilter.getValue();
    }

    private AttributeMapFilter doCreate() {
        return securityContext.asProcessingUserResult(() -> {
            final List<AttributeMapFilter> filters = new ArrayList<>();
            final ReceiveDataConfig receiveDataConfig = receiveDataConfigProvider.get();

            // !!!! ORDER IS IMPORTANT !!!!
            // Some filters depend on others, so the order in the list matters.

            // Validate the stream type, if there is one
            filters.add(streamTypeValidatorProvider.get());

            // This one validates the feed attr, or if it is not there (and config allows)
            // generates it, so needs to go before most others.
            filters.add(feedNameCheckAttributeMapFilterProvider.get());

            final ReceiptCheckMode receiptCheckMode = receiveDataConfig.getReceiptCheckMode();
            filters.add(getReceiptCheckFilter(receiptCheckMode));

            // Auto-create the feed/pipe/procFilter/userGrp/etc, if configured.
            // On Proxy this will always be a ReceiveAll filter
            filters.add(contentAutoCreationAttrMapFilterFactory.create());

            // This copes with nulls and compacts down the filter chain if there are
            // ReceiveAll filters in there.
            final AttributeMapFilter filterChain = AttributeMapFilter.wrap(filters);
            LOGGER.debug("doCreate() - receiptCheckMode: {}, filterChain: {}", receiptCheckMode, filterChain);
            return filterChain;
        });
    }

    private AttributeMapFilter getReceiptCheckFilter(final ReceiptCheckMode receiptCheckMode) {
        return switch (receiptCheckMode) {
            // The feed status filter will determine if the feed status check needs
            // to happen as the config for it is different between proxy and stroom.
            // Proxy and stroom each have a different FeedStatusService impl bound.
            case FEED_STATUS -> feedStatusAttributeMapFilterProvider.get();
            case RECEIPT_POLICY -> dataReceiptPolicyAttributeMapFilterFactory.create();
            case RECEIVE_ALL -> ReceiveAllAttributeMapFilter.getInstance();
            case REJECT_ALL -> RejectAllAttributeMapFilter.getInstance();
            case DROP_ALL -> DropAllAttributeMapFilter.getInstance();
        };
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
