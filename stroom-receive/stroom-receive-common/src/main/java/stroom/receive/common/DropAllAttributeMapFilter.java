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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

public class DropAllAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DropAllAttributeMapFilter.class);

    public static final DropAllAttributeMapFilter INSTANCE = new DropAllAttributeMapFilter();

    private DropAllAttributeMapFilter() {
    }

    public static DropAllAttributeMapFilter getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        LOGGER.debug("Drop all filter - attributeMap: {}", attributeMap);
        return false;
    }

    @Override
    public String getName() {
        return "Drop ALL";
    }

    @Override
    public String toString() {
        return getName();
    }
}
