/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.receive.common;

import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.security.api.CommonSecurityContext;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
    private final Provider<StreamTypeValidator> streamTypeValidatorProvider;
    private final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider;
    private final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider;
    private final Provider<FeedExistenceAttributeMapFilter> feedExistenceAttributeMapFilterProvider;
    private final Provider<DataReceiptPolicyAttributeMapFilterFactory> dataReceiptPolicyAttrMapFilterFactoryProvider;
    private final CommonSecurityContext securityContext;
    private final ContentAutoCreationAttrMapFilterFactory contentAutoCreationAttrMapFilterFactory;
    private final CachedValue<AttributeMapFilter, Void> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<StreamTypeValidator> streamTypeValidatorProvider,
            final Provider<FeedNameCheckAttributeMapFilter> feedNameCheckAttributeMapFilterProvider,
            final Provider<FeedStatusAttributeMapFilter> feedStatusAttributeMapFilterProvider,
            final Provider<FeedExistenceAttributeMapFilter> feedExistenceAttributeMapFilterProvider,
            final Provider<DataReceiptPolicyAttributeMapFilterFactory> dataReceiptPolicyAttrMapFilterFactoryProvider,
            final CommonSecurityContext securityContext,
            final ContentAutoCreationAttrMapFilterFactory contentAutoCreationAttrMapFilterFactory) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.streamTypeValidatorProvider = streamTypeValidatorProvider;
        this.feedNameCheckAttributeMapFilterProvider = feedNameCheckAttributeMapFilterProvider;
        this.feedStatusAttributeMapFilterProvider = feedStatusAttributeMapFilterProvider;
        this.feedExistenceAttributeMapFilterProvider = feedExistenceAttributeMapFilterProvider;
        this.securityContext = securityContext;
        this.contentAutoCreationAttrMapFilterFactory = contentAutoCreationAttrMapFilterFactory;
        this.dataReceiptPolicyAttrMapFilterFactoryProvider = dataReceiptPolicyAttrMapFilterFactoryProvider;

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
            filters.addAll(getReceiptCheckFilters(receiptCheckMode));

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

    private List<AttributeMapFilter> getReceiptCheckFilters(final ReceiptCheckMode receiptCheckMode) {
        return switch (receiptCheckMode) {
            // This will check the feed existence and status in one go
            case FEED_STATUS -> List.of(feedStatusAttributeMapFilterProvider.get());
            // Need to check feed exists after passing all the policy rules
            case RECEIPT_POLICY -> List.of(
                    dataReceiptPolicyAttrMapFilterFactoryProvider.get().create(),
                    feedExistenceAttributeMapFilterProvider.get());
            // Receiving everything
            case RECEIVE_ALL -> List.of(
                    feedExistenceAttributeMapFilterProvider.get());
            case REJECT_ALL -> List.of(RejectAllAttributeMapFilter.getInstance());
            case DROP_ALL -> List.of(DropAllAttributeMapFilter.getInstance());
        };
    }

//    private AttributeMapFilter createFallbackFilter(final ReceiveAction fallbackReceiveAction) {
//        final ReceiveAction receiveAction = Objects.requireNonNullElse(
//                fallbackReceiveAction,
//                ReceiveDataConfig.DEFAULT_FALLBACK_RECEIVE_ACTION);
//        return switch (receiveAction) {
//            case RECEIVE -> ReceiveAllAttributeMapFilter.getInstance();
//            case REJECT -> RejectAllAttributeMapFilter.getInstance();
//            case DROP -> DropAllAttributeMapFilter.getInstance();
//        };
//    }

    public AttributeMapFilter create() {
        // Async so we don't hold up receipt while waiting for a response to come
        // back from downstream and the filters to be constructed.
        return updatableAttributeMapFilter.getValueAsync();
    }
}
