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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TestStepDataStoreManager {

    private static final ElementId E1 = new ElementId("combinedParser");

    private StepDataStoreManager newManager(final Path tempDir) {
        final TempDirProvider tempDirProvider = () -> tempDir;
        return new StepDataStoreManager(tempDirProvider, new SteppingConfig());
    }

    private CapturedElementData data(final String value) {
        return new CapturedElementData(CapturedData.text(value), CapturedData.text(value),
                false, false, true, null);
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
    void testCleanupOrphansDeletesOnlyStrandedOldDirs(@TempDir final Path tempDir) throws Exception {
        final StepDataStoreManager manager = newManager(tempDir);

        // A live session - its data is in use and must survive however old it looks.
        manager.getOrCreateStore("live", 100L)
                .putElementData(new StepLocation(100L, 0, 0), E1, "fp", data("a"));
        final Path liveDir = manager.getSessionDir("live");
        Files.setLastModifiedTime(liveDir, FileTime.from(Instant.now().minus(Duration.ofDays(1))));

        // Stranded by a hard shutdown: no live session owns it, and it is older than orphanMaxAge (1h).
        final Path orphanDir = manager.getBaseDir().resolve("orphan");
        Files.createDirectories(orphanDir.resolve("100"));
        Files.setLastModifiedTime(orphanDir, FileTime.from(Instant.now().minus(Duration.ofDays(1))));

        // Unowned but recent - could belong to a session mid-creation, so it must be left alone.
        final Path recentDir = manager.getBaseDir().resolve("recent");
        Files.createDirectories(recentDir.resolve("100"));

        manager.cleanupOrphans();

        assertThat(Files.exists(orphanDir)).isFalse();
        assertThat(Files.exists(liveDir)).isTrue();
        assertThat(Files.exists(recentDir)).isTrue();
        // The live session's data is still readable.
        assertThat(manager.getOrCreateStore("live", 100L)
                .getElementData(new StepLocation(100L, 0, 0), E1, "fp"))
                .map(CapturedElementData::outputText).contains("a");
    }

    @Test
    void testDeleteAllSessionsClearsTheBaseDir(@TempDir final Path tempDir) {
        final StepDataStoreManager manager = newManager(tempDir);
        manager.getOrCreateStore("s1", 1L).putElementData(new StepLocation(1L, 0, 0), E1, "fp", data("a"));
        manager.getOrCreateStore("s2", 2L).putElementData(new StepLocation(2L, 0, 0), E1, "fp", data("b"));

        manager.deleteAllSessions();

        assertThat(Files.exists(manager.getSessionDir("s1"))).isFalse();
        assertThat(Files.exists(manager.getSessionDir("s2"))).isFalse();
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
                .map(CapturedElementData::outputText).contains("a");
        assertThat(manager.getOrCreateStore("sessionB", 1L)
                .getElementData(new StepLocation(1L, 0, 0), E1, "fp"))
                .map(CapturedElementData::outputText).contains("b");
    }
}
