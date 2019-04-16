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

package stroom.proxy.handler;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.datafeed.server.DataReceiptPolicyMetaMapFilterFactory;
import stroom.datafeed.server.FeedStatusMetaMapFilter;
import stroom.datafeed.server.MetaMapFilter;
import stroom.datafeed.server.PermissiveMetaMapFilter;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class MetaMapFilterFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaMapFilterFactory.class);

    private final MetaMapFilter metaMapFilter;

    @Inject
    public MetaMapFilterFactory(final ProxyRequestConfig proxyRequestConfig,
                                final DataReceiptPolicyMetaMapFilterFactory dataReceiptPolicyMetaMapFilterFactory,
                                final Provider<RemoteFeedStatusService> remoteFeedStatusServiceProvider) {
        if (StringUtils.isNotBlank(proxyRequestConfig.getReceiptPolicyUuid())) {
            LOGGER.info("Using data receipt policy to filter received data");
            metaMapFilter = dataReceiptPolicyMetaMapFilterFactory.create(proxyRequestConfig.getReceiptPolicyUuid());
        } else if (StringUtils.isNotBlank(proxyRequestConfig.getFeedStatusUrl())) {
            LOGGER.info("Using remote feed status service to filter received data");
            final RemoteFeedStatusService remoteFeedStatusService = remoteFeedStatusServiceProvider.get();
            metaMapFilter = new FeedStatusMetaMapFilter(remoteFeedStatusService);
        } else {
            LOGGER.info("Permitting receipt of all data");
            metaMapFilter = new PermissiveMetaMapFilter();
        }
    }

    public MetaMapFilter create() {
        return metaMapFilter;
    }
}
