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

import stroom.meta.api.AttributeMap;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactoryImpl.Checker;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Objects;

class DataReceiptPolicyAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataReceiptPolicyAttributeMapFilter.class);

    private final Checker checker;

    DataReceiptPolicyAttributeMapFilter(final Checker checker) {
        Objects.requireNonNull(checker, "Null policy checker");
        this.checker = checker;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        // We need to examine the meta map and ensure we aren't dropping or rejecting this data.
        // Will throw a StroomStreamException if rejected, but handle REJECT just in case.
        final ReceiveAction action = checker.check(attributeMap);
        final boolean filterOutcome = action.toFilterResultOrThrow(() ->
                new StroomStreamException(StroomStatusCode.REJECTED_BY_POLICY_RULES, attributeMap));
        LOGGER.debug("filter() - filterOutcome: {}, action: {}, attributeMap: {}",
                filterOutcome, action, attributeMap);
        return filterOutcome;
    }
}
