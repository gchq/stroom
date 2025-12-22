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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class RejectAllAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RejectAllAttributeMapFilter.class);

    public static final RejectAllAttributeMapFilter INSTANCE = new RejectAllAttributeMapFilter();

    private RejectAllAttributeMapFilter() {
    }

    public static RejectAllAttributeMapFilter getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        LOGGER.debug("Reject all filter - attributeMap: {}", attributeMap);
        throw new StroomStreamException(StroomStatusCode.REJECTED_BY_POLICY_RULES, attributeMap);
    }

    @Override
    public String getName() {
        return "Reject ALL";
    }

    @Override
    public String toString() {
        return getName();
    }
}
