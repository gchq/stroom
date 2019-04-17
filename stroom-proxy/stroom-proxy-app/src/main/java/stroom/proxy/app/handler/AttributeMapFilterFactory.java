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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.FeedStatusAttributeMapFilter;
import stroom.receive.common.PermissiveAttributeMapFilter;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class AttributeMapFilterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttributeMapFilterFactory.class);

    private final AttributeMapFilter attributeMapFilter;

    @Inject
    public AttributeMapFilterFactory(final ProxyRequestConfig proxyRequestConfig,
                                     final FeedStatusConfig feedStatusConfig,
                                     final DataReceiptPolicyAttributeMapFilterFactory dataReceiptPolicyAttributeMapFilterFactory,
                                     final Provider<RemoteFeedStatusService> remoteFeedStatusServiceProvider) {
        if (StringUtils.isNotBlank(proxyRequestConfig.getReceiptPolicyUuid())) {
            LOGGER.info("Using data receipt policy to filter received data");
            attributeMapFilter = dataReceiptPolicyAttributeMapFilterFactory.create(new DocRef("RuleSet", proxyRequestConfig.getReceiptPolicyUuid()));
        } else if (StringUtils.isNotBlank(feedStatusConfig.getFeedStatusUrl())) {
            LOGGER.info("Using remote feed status service to filter received data");
            final RemoteFeedStatusService remoteFeedStatusService = remoteFeedStatusServiceProvider.get();
            attributeMapFilter = new FeedStatusAttributeMapFilter(remoteFeedStatusService);
        } else {
            LOGGER.info("Permitting receipt of all data");
            attributeMapFilter = new PermissiveAttributeMapFilter();
        }
    }

    public AttributeMapFilter create() {
        return attributeMapFilter;
    }
}
