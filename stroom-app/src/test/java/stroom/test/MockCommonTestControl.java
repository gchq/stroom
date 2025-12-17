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

package stroom.test;

import stroom.util.shared.Clearable;

import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.Set;

/**
 * Version of the test control used with the mocks.
 */
public class MockCommonTestControl implements CommonTestControl {

    private final Set<Clearable> clearables;

    @Inject
    MockCommonTestControl(final Set<Clearable> clearables) {
        this.clearables = clearables;
    }

    @Override
    public void setup(final Path tempDir) {
    }

    @Override
    public void cleanup() {
        clearables.forEach(Clearable::clear);
    }

    @Override
    public void clear() {
        clearables.forEach(Clearable::clear);
    }

    @Override
    public void createRequiredXMLSchemas() {
        // NA
    }
}
