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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactoryImpl.Checker;
import stroom.receive.rules.shared.RuleAction;

import java.util.Objects;

class DataReceiptPolicyAttributeMapFilter implements AttributeMapFilter {

    private final Checker checker;
//    private final UnaryOperator<AttributeMap> attributeMapConverter;

    DataReceiptPolicyAttributeMapFilter(final Checker checker) {
        Objects.requireNonNull(checker, "Null policy checker");
        this.checker = checker;
//        this.attributeMapConverter = Objects.requireNonNullElseGet(attributeMapConverter, UnaryOperator::identity);
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
//        final AttributeMap effectiveAttributeMap = attributeMapConverter.apply(attributeMap);
        // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
        final RuleAction action = checker.check(attributeMap);

        if (RuleAction.REJECT.equals(action)) {
            throw new StroomStreamException(StroomStatusCode.FEED_IS_NOT_SET_TO_RECEIVE_DATA, attributeMap);
        }

        return RuleAction.RECEIVE.equals(action);
    }
}
