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

package stroom.datafeed.server;

import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Singleton
public class MetaMapFilterFactory {
    private final StroomPropertyService stroomPropertyService;
    private final DataReceiptPolicyMetaMapFilterFactory dataReceiptPolicyMetaMapFilterFactory;
    private final FeedStatusMetaMapFilter feedStatusMetaMapFilter;

    private volatile MetaMapFilter metaMapFilter;
    private final AtomicReference<String> lastPolicyUuid = new AtomicReference<>();

    @Inject
    public MetaMapFilterFactory(final StroomPropertyService stroomPropertyService,
                                final DataReceiptPolicyMetaMapFilterFactory dataReceiptPolicyMetaMapFilterFactory,
                                final FeedStatusMetaMapFilter feedStatusMetaMapFilter) {
        this.stroomPropertyService = stroomPropertyService;
        this.dataReceiptPolicyMetaMapFilterFactory = dataReceiptPolicyMetaMapFilterFactory;
        this.feedStatusMetaMapFilter = feedStatusMetaMapFilter;
    }

    public MetaMapFilter create() {
        final String receiptPolicyUuid = stroomPropertyService.getProperty("stroom.feed.receiptPolicyUuid");
        final String last = lastPolicyUuid.get();
        if (metaMapFilter == null || !Objects.equals(last, receiptPolicyUuid)) {
            lastPolicyUuid.compareAndSet(last, receiptPolicyUuid);

            if (receiptPolicyUuid != null && receiptPolicyUuid.length() > 0) {
                metaMapFilter = dataReceiptPolicyMetaMapFilterFactory.create(receiptPolicyUuid);
            } else {
                metaMapFilter = feedStatusMetaMapFilter;
            }
        }

        return metaMapFilter;
    }
}
