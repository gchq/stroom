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

package stroom.core.receive;

import stroom.docref.DocRef;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AuthenticationType;
import stroom.receive.common.DataFeedKeyService;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.FeedStatusAttributeMapFilter;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.util.concurrent.PeriodicallyUpdatedValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AttributeMapFilterFactory {

    private final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory;
    private final FeedStatusAttributeMapFilter feedStatusAttributeMapFilter;
    private final DataFeedKeyService dataFeedKeyService;
    private final PeriodicallyUpdatedValue<AttributeMapFilter, ConfigState> updatableAttributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory,
            final FeedStatusAttributeMapFilter feedStatusAttributeMapFilter,
            final DataFeedKeyService dataFeedKeyService) {

        this.dataReceiptPolicyAttributeMapFilterFactory = dataReceiptPolicyAttributeMapFilterFactory;
        this.feedStatusAttributeMapFilter = feedStatusAttributeMapFilter;
        this.dataFeedKeyService = dataFeedKeyService;

        // Every 60s, see if config has changed and if so create a new filter
        this.updatableAttributeMapFilter = new PeriodicallyUpdatedValue<>(
                Duration.ofSeconds(60),
                this::create,
                () -> ConfigState.fromConfig(receiveDataConfigProvider.get()));
    }

    private AttributeMapFilter create(final ConfigState configState) {

        final List<AttributeMapFilter> filters = new ArrayList<>();
//        if (configState.isEnabled(AuthenticationType.DATA_FEED_KEY)) {
//            filters.add(dataFeedKeyService);
//        }
//        if (NullSafe.isNonEmptyString(configState.policyUuid)) {
        if (configState.policyUuid != null && !configState.policyUuid.isEmpty()) {
            filters.add(dataReceiptPolicyAttributeMapFilterFactory.create(
                    new DocRef(ReceiveDataRules.DOCUMENT_TYPE, configState.policyUuid)));
        }
        filters.add(feedStatusAttributeMapFilter);
        return AttributeMapFilter.wrap(filters);
    }

    public AttributeMapFilter create() {
        return updatableAttributeMapFilter.getValue();
    }


    // --------------------------------------------------------------------------------


    private record ConfigState(
            String policyUuid,
            Set<AuthenticationType> enabledAuthenticationTypes) {

        public static ConfigState fromConfig(final ReceiveDataConfig receiveDataConfig) {
            return new ConfigState(
                    receiveDataConfig.getReceiptPolicyUuid(),
                    receiveDataConfig.getEnabledAuthenticationTypes());
        }

        public boolean isEnabled(final AuthenticationType authenticationType) {
            return enabledAuthenticationTypes.contains(authenticationType);
        }
    }
}
