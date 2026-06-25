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

package stroom.meta.impl.db;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;

import java.util.Set;

public class Cleanup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Cleanup.class);

    private final Set<Clearable> clearables;

    @Inject
    Cleanup(final Set<Clearable> clearables) {
        this.clearables = clearables;
    }

    public void cleanup() {
        // Clear all caches or files that might have been created by previous tests.
        clearables.forEach(clearable -> {
            LOGGER.debug("Clearing {}", clearable.getClass().getSimpleName());
            clearable.clear();
        });
    }
}
