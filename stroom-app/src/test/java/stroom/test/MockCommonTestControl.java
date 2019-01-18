/*
 * Copyright 2016 Crown Copyright
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

import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.entity.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Set;

/**
 * Version of the test control used with the mocks.
 */
public class MockCommonTestControl implements CommonTestControl {
    private final Set<Clearable> clearables;
    private final Provider<MemoryPersistence> memoryPersistenceProvider;

    @Inject
    MockCommonTestControl(final Set<Clearable> clearables, final Provider<MemoryPersistence> memoryPersistenceProvider) {
        this.clearables = clearables;
        this.memoryPersistenceProvider = memoryPersistenceProvider;
    }

    @Override
    public void setup() {
    }

    @Override
    public void teardown() {
        clearables.forEach(Clearable::clear);

        try {
            final MemoryPersistence memoryPersistence = memoryPersistenceProvider.get();
            memoryPersistence.clear();
        } catch (final RuntimeException e) {
            // Ignore.
        }
    }

    @Override
    public int countEntity(final String tableName) {
        return 0;
    }

    @Override
    public void createRequiredXMLSchemas() {
        // NA
    }
}
