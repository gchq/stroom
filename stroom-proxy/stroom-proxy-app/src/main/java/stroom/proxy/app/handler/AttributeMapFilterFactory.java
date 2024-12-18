/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.docref.DocRef;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.DataFeedKeyService;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.FeedStatusAttributeMapFilter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.NullSafe;
import stroom.util.concurrent.PeriodicallyUpdatedValue;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class AttributeMapFilterFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeMapFilterFactory.class);

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;
    private final Provider<FeedStatusConfig> feedStatusConfigProvider;
    private final Provider<RemoteFeedStatusService> remoteFeedStatusServiceProvider;
    private final DataFeedKeyService dataFeedKeyService;

    private final PeriodicallyUpdatedValue<AttributeMapFilter, ConfigState> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final Provider<FeedStatusConfig> feedStatusConfigProvider,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory,
            final Provider<RemoteFeedStatusService> remoteFeedStatusServiceProvider,
            final DataFeedKeyService dataFeedKeyService) {

        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.feedStatusConfigProvider = feedStatusConfigProvider;
        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;
        this.remoteFeedStatusServiceProvider = remoteFeedStatusServiceProvider;
        this.dataFeedKeyService = dataFeedKeyService;

        // Every 60s, see if config has changed and if so create a new filter
        this.updatableAttributeMapFilter = new PeriodicallyUpdatedValue<>(
                Duration.ofSeconds(60),
                this::create,
                () -> ConfigState.fromConfig(
                        receiveDataConfigProvider.get(),
                        feedStatusConfigProvider.get()));
    }

    private AttributeMapFilter create(final ConfigState configState) {
        final List<AttributeMapFilter> filters = new ArrayList<>();

        if (configState.isDatafeedKeyAuthenticationEnabled()) {
            LOGGER.debug("Adding data feed key filter");
            filters.add(dataFeedKeyService);
        }

        if (NullSafe.isNonBlankString(configState.receiptPolicyUuid)) {
            LOGGER.debug("Adding data receipt policy filter");
            filters.add(dataReceiptPolicyAttributeMapFilterFactory.create(
                    new DocRef(ReceiveDataRules.DOCUMENT_TYPE, configState.receiptPolicyUuid)));
        }

        if (NullSafe.isNonBlankString(configState.feedStatusUrl)) {
            LOGGER.debug("Adding remote feed status service filter");
            final RemoteFeedStatusService remoteFeedStatusService = remoteFeedStatusServiceProvider.get();
            filters.add(new FeedStatusAttributeMapFilter(remoteFeedStatusService));
        }

        return AttributeMapFilter.wrap(filters);
    }

    public AttributeMapFilter create() {
        return updatableAttributeMapFilter.getValue();
    }


    // --------------------------------------------------------------------------------


    private record ConfigState(
            String receiptPolicyUuid,
            boolean isDatafeedKeyAuthenticationEnabled,
            String feedStatusUrl) {

        public static ConfigState fromConfig(
                final ReceiveDataConfig receiveDataConfig,
                final FeedStatusConfig feedStatusConfig) {

            return new ConfigState(
                    receiveDataConfig.getReceiptPolicyUuid(),
                    receiveDataConfig.isDatafeedKeyAuthenticationEnabled(),
                    feedStatusConfig.getFeedStatusUrl());
        }
    }
}
