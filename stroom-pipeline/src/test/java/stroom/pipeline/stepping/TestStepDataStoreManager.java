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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestStepDataStoreManager {

    private static final ElementId E1 = new ElementId("combinedParser");

    private StepDataStoreManager newManager(final Path tempDir) {
        final TempDirProvider tempDirProvider = () -> tempDir;
        return new StepDataStoreManager(tempDirProvider, new SteppingConfig());
    }

    private SharedElementData data(final String value) {
        return new SharedElementData(value, value, null, false, false, true);
    }

    @Test
    void testStoresAreScopedPerSessionAndStream(@TempDir final Path tempDir) {
        final StepDataStoreManager manager = newManager(tempDir);

        final StepDataStore streamA = manager.getOrCreateStore("session1", 100L);
        final StepDataStore streamB = manager.getOrCreateStore("session1", 200L);
        assertThat(streamA).isNotSameAs(streamB);
        // Same (session, stream) returns the same store instance.
        assertThat(manager.getOrCreateStore("session1", 100L)).isSameAs(streamA);

        streamA.putElementData(new StepLocation(100L, 0, 0), E1, "fp", data("a"));

        // Each requested stream gets its own directory under the session.
        assertThat(Files.exists(manager.getSessionDir("session1").resolve("100"))).isTrue();
        assertThat(Files.exists(manager.getSessionDir("session1").resolve("200"))).isTrue();
    }

    @Test
    void testDeleteSessionRemovesEverything(@TempDir final Path tempDir) {
        final StepDataStoreManager manager = newManager(tempDir);
        final StepDataStore store = manager.getOrCreateStore("session1", 100L);
        store.putElementData(new StepLocation(100L, 0, 0), E1, "fp", data("a"));

        final Path sessionDir = manager.getSessionDir("session1");
        assertThat(Files.exists(sessionDir)).isTrue();

        manager.deleteSession("session1");

        assertThat(Files.exists(sessionDir)).isFalse();
        // A subsequent session for the same stream id gets a fresh store.
        final StepDataStore fresh = manager.getOrCreateStore("session1", 100L);
        assertThat(fresh.getRecordCount(0)).isZero();
    }

    @Test
    void testDifferentSessionsAreIsolated(@TempDir final Path tempDir) {
        final StepDataStoreManager manager = newManager(tempDir);
        manager.getOrCreateStore("sessionA", 1L)
                .putElementData(new StepLocation(1L, 0, 0), E1, "fp", data("a"));
        manager.getOrCreateStore("sessionB", 1L)
                .putElementData(new StepLocation(1L, 0, 0), E1, "fp", data("b"));

        assertThat(manager.getOrCreateStore("sessionA", 1L)
                .getElementData(new StepLocation(1L, 0, 0), E1, "fp"))
                .map(SharedElementData::getOutput).contains("a");
        assertThat(manager.getOrCreateStore("sessionB", 1L)
                .getElementData(new StepLocation(1L, 0, 0), E1, "fp"))
                .map(SharedElementData::getOutput).contains("b");
    }
}
