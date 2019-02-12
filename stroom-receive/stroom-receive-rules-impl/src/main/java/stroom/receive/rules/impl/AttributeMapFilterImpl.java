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

package stroom.receive.rules.impl;

import stroom.meta.shared.AttributeMap;
import stroom.receive.AttributeMapFilter;
import stroom.receive.common.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.receive.rules.shared.RuleAction;

class AttributeMapFilterImpl implements AttributeMapFilter {
    private final ReceiveDataPolicyChecker dataReceiptPolicyChecker;

    AttributeMapFilterImpl(final ReceiveDataPolicyChecker dataReceiptPolicyChecker) {
        this.dataReceiptPolicyChecker = dataReceiptPolicyChecker;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        boolean allowThrough = true;
        if (dataReceiptPolicyChecker != null) {
            // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
            final RuleAction dataReceiptAction = dataReceiptPolicyChecker.check(attributeMap);

            if (RuleAction.REJECT.equals(dataReceiptAction)) {
                throw new StroomStreamException(StroomStatusCode.RECEIPT_POLICY_SET_TO_REJECT_DATA);
            }

            allowThrough = RuleAction.RECEIVE.equals(dataReceiptAction);
        }
        return allowThrough;
    }
}
