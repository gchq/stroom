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
 *
 */

package stroom.ruleset.server;

import stroom.datafeed.server.MetaMapFilter;
import stroom.feed.MetaMap;
import stroom.feed.StroomStreamException;
import stroom.proxy.StroomStatusCode;
import stroom.ruleset.shared.DataReceiptAction;

import java.util.Objects;

class DataReceiptPolicyMetaMapFilter implements MetaMapFilter {
    private final DataReceiptPolicyChecker dataReceiptPolicyChecker;

    DataReceiptPolicyMetaMapFilter(final DataReceiptPolicyChecker dataReceiptPolicyChecker) {
        Objects.requireNonNull(dataReceiptPolicyChecker, "Null policy checker");
        this.dataReceiptPolicyChecker = dataReceiptPolicyChecker;
    }

    @Override
    public boolean filter(final MetaMap metaMap) {
        // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
        final DataReceiptAction dataReceiptAction = dataReceiptPolicyChecker.check(metaMap);

        if (DataReceiptAction.REJECT.equals(dataReceiptAction)) {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVED_DATA);
        }

        return DataReceiptAction.RECEIVE.equals(dataReceiptAction);
    }
}
