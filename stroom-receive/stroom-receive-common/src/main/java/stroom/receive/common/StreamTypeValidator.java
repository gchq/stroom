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
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.Set;

public class StreamTypeValidator implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamTypeValidator.class);

    private final Set<String> validStreamTypes;

    @Inject
    public StreamTypeValidator(final ReceiveDataConfig receiveDataConfig) {
        this.validStreamTypes = NullSafe.set(receiveDataConfig.getMetaTypes());
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        // Get the type name from the header arguments if supplied.
        final String type = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));
        LOGGER.debug("filter() - type: {}, attributeMap: {}", type, attributeMap);
        if (!type.isEmpty() && !validStreamTypes.contains(type)) {
            LOGGER.debug("filter() - invalid type: {}, validStreamTypes: {}", type, validStreamTypes);
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }
        return true;
    }
}
