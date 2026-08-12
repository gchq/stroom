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

package stroom.util.xml;

import java.util.concurrent.atomic.AtomicBoolean;

public class SAXParserSettings {

    private static final AtomicBoolean SECURE_PROCESSING = new AtomicBoolean(true);
    // Secure by default: disallow DOCTYPE declarations and external entity/DTD resolution to prevent XXE.
    private static final AtomicBoolean EXTERNAL_ENTITIES_DISABLED = new AtomicBoolean(true);

    public static boolean isExternalEntitiesDisabled() {
        return EXTERNAL_ENTITIES_DISABLED.get();
    }

    public static void setExternalEntitiesDisabled(final boolean isDisabled) {
        EXTERNAL_ENTITIES_DISABLED.set(isDisabled);
    }

    public static boolean isSecureProcessingEnabled() {
        return SECURE_PROCESSING.get();
    }

    public static void setSecureProcessingEnabled(final boolean isEnabled) {
        SECURE_PROCESSING.set(isEnabled);
    }
}
