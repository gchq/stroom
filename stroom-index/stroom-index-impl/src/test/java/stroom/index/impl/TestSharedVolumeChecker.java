/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestSharedVolumeChecker {

    private static final int VOLUME_ID = 7;
    private static final Duration GRACE_PERIOD = Duration.ofMinutes(15);
    // Long enough that markers written at the start of a test are still fresh at the end of it.
    private static final Duration STALE_AFTER = Duration.ofHours(1);
    private static final long START_MS = 1_000_000L;
    private static final long AFTER_GRACE_MS = START_MS + GRACE_PERIOD.toMillis();

    @Test
    void testSharedMount_noProblem(@TempDir final Path sharedMount) {
        // Two nodes that see the same directory each find the other's marker.
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);
        final SharedVolumeChecker node2 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);

        node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), START_MS);
        node2.check(VOLUME_ID, sharedMount, "node2", List.of("node1"), START_MS);

        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), AFTER_GRACE_MS)).isEmpty();
        assertThat(node2.check(VOLUME_ID, sharedMount, "node2", List.of("node1"), AFTER_GRACE_MS)).isEmpty();
        assertThat(node1.getProblems()).isEmpty();
        assertThat(node2.getProblems()).isEmpty();
    }

    @Test
    void testLocalDiskMarkedShared_reported(@TempDir final Path node1Disk, @TempDir final Path node2Disk) {
        // Each node has its own disk at the path, so neither ever sees the other's marker.
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);
        final SharedVolumeChecker node2 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);

        // Nothing is reported at first, as the other node may not have written its marker yet.
        assertThat(node1.check(VOLUME_ID, node1Disk, "node1", List.of("node2"), START_MS)).isEmpty();
        assertThat(node2.check(VOLUME_ID, node2Disk, "node2", List.of("node1"), START_MS)).isEmpty();
        assertThat(node1.check(VOLUME_ID, node1Disk, "node1", List.of("node2"), AFTER_GRACE_MS - 1)).isEmpty();
        assertThat(node1.getProblems()).isEmpty();

        // Once the grace period has passed the missing markers are reported.
        assertThat(node1.check(VOLUME_ID, node1Disk, "node1", List.of("node2"), AFTER_GRACE_MS))
                .containsExactly("node2");
        assertThat(node2.check(VOLUME_ID, node2Disk, "node2", List.of("node1"), AFTER_GRACE_MS))
                .containsExactly("node1");
        assertThat(node1.getProblems()).containsOnlyKeys(VOLUME_ID);
        assertThat(node1.getProblems().get(VOLUME_ID)).containsExactly("node2");
    }

    @Test
    void testProblemClearsWhenMarkerAppears(@TempDir final Path sharedMount) {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);
        node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2", "node3"), START_MS);
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node3", "node2"), AFTER_GRACE_MS))
                .containsExactly("node2", "node3");

        // node2 starts and writes its marker.
        new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER)
                .check(VOLUME_ID, sharedMount, "node2", List.of(), AFTER_GRACE_MS);

        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2", "node3"), AFTER_GRACE_MS + 1))
                .containsExactly("node3");

        // node3 is disabled, so it is no longer expected.
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), AFTER_GRACE_MS + 2)).isEmpty();
        assertThat(node1.getProblems()).isEmpty();
    }

    @Test
    void testGracePeriodRestartsForAReturningNode(@TempDir final Path sharedMount) {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);
        node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), START_MS);
        // node2 stops being expected and then is expected again much later.
        node1.check(VOLUME_ID, sharedMount, "node1", List.of(), START_MS + 1);

        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), AFTER_GRACE_MS)).isEmpty();
        assertThat(node1.check(
                VOLUME_ID, sharedMount, "node1", List.of("node2"), AFTER_GRACE_MS + GRACE_PERIOD.toMillis()))
                .containsExactly("node2");
    }

    @Test
    void testThisNodeIsNeverReported(@TempDir final Path sharedMount) {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(Duration.ZERO, STALE_AFTER);

        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node1"), START_MS)).isEmpty();
    }

    @Test
    void testVolumesAreCheckedSeparately(@TempDir final Path sharedMount, @TempDir final Path localDisk) {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(Duration.ZERO, STALE_AFTER);
        new SharedVolumeChecker(Duration.ZERO, STALE_AFTER).check(1, sharedMount, "node2", List.of(), START_MS);

        assertThat(node1.check(1, sharedMount, "node1", List.of("node2"), START_MS)).isEmpty();
        assertThat(node1.check(2, localDisk, "node1", List.of("node2"), START_MS)).containsExactly("node2");
        assertThat(node1.getProblems()).containsOnlyKeys(2);

        node1.forget(2);
        assertThat(node1.getProblems()).isEmpty();
    }

    @Test
    void testRetainForgetsVolumesStillInGracePeriod(@TempDir final Path localDisk) {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);
        node1.check(1, localDisk, "node1", List.of("node2"), START_MS);
        node1.check(2, localDisk, "node1", List.of("node2"), START_MS);

        // Volume 1 is deleted and a volume is later given the same id, so its grace period starts again.
        node1.retain(List.of(2));

        assertThat(node1.check(1, localDisk, "node1", List.of("node2"), AFTER_GRACE_MS)).isEmpty();
        assertThat(node1.check(2, localDisk, "node1", List.of("node2"), AFTER_GRACE_MS)).containsExactly("node2");

        node1.retain(List.of());
        assertThat(node1.getProblems()).isEmpty();
    }

    @Test
    void testMarkerFile(@TempDir final Path sharedMount) throws IOException {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, STALE_AFTER);
        node1.check(VOLUME_ID, sharedMount, "node1", List.of(), START_MS);

        final Path markerDir = sharedMount.resolve(SharedVolumeChecker.MARKER_DIR_NAME);
        final Path markerFile = SharedVolumeChecker.getMarkerFile(markerDir, "node1");
        assertThat(markerFile).isRegularFile();
        assertThat(Files.readString(markerFile)).isEqualTo(String.valueOf(START_MS));

        // The marker is rewritten on every check so that other nodes can see this node still has the volume.
        node1.check(VOLUME_ID, sharedMount, "node1", List.of(), START_MS + 5);
        assertThat(Files.readString(markerFile)).isEqualTo(String.valueOf(START_MS + 5));
    }

    @Test
    void testMountThatFailsLaterIsReported(@TempDir final Path sharedMount) {
        final Duration staleAfter = Duration.ofMinutes(15);
        final SharedVolumeChecker node1 = new SharedVolumeChecker(GRACE_PERIOD, staleAfter);
        final SharedVolumeChecker node2 = new SharedVolumeChecker(GRACE_PERIOD, staleAfter);
        node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), START_MS);
        node2.check(VOLUME_ID, sharedMount, "node2", List.of("node1"), START_MS);

        // node2's mount fails, so it stops refreshing its marker on the real mount while node1 carries on.
        final long staleMs = START_MS + staleAfter.toMillis();
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), staleMs - 1)).isEmpty();
        // The marker is now too old, which starts the grace period.
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), staleMs)).isEmpty();
        assertThat(node1.check(
                VOLUME_ID, sharedMount, "node1", List.of("node2"), staleMs + GRACE_PERIOD.toMillis()))
                .containsExactly("node2");

        // The mount comes back and node2 refreshes its marker.
        final long recoveredMs = staleMs + GRACE_PERIOD.toMillis() + 1;
        node2.check(VOLUME_ID, sharedMount, "node2", List.of("node1"), recoveredMs);
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), recoveredMs)).isEmpty();
        assertThat(node1.getProblems()).isEmpty();
    }

    @Test
    void testNodeThatIsDownIsNotExpected(@TempDir final Path sharedMount) {
        final Duration staleAfter = Duration.ofMinutes(15);
        final SharedVolumeChecker node1 = new SharedVolumeChecker(Duration.ZERO, staleAfter);
        new SharedVolumeChecker(Duration.ZERO, staleAfter)
                .check(VOLUME_ID, sharedMount, "node2", List.of(), START_MS);

        // node2 goes down, so the caller no longer lists it and its old marker raises no problem.
        final long muchLaterMs = START_MS + Duration.ofDays(1).toMillis();
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of(), muchLaterMs)).isEmpty();
        // Were it listed, the old marker would count as missing.
        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), muchLaterMs))
                .containsExactly("node2");
    }

    @Test
    void testUnreadableMarkerCountsAsMissing(@TempDir final Path sharedMount) throws IOException {
        final SharedVolumeChecker node1 = new SharedVolumeChecker(Duration.ZERO, STALE_AFTER);
        final Path markerDir = sharedMount.resolve(SharedVolumeChecker.MARKER_DIR_NAME);
        Files.createDirectories(markerDir);
        Files.writeString(SharedVolumeChecker.getMarkerFile(markerDir, "node2"), "not a time");

        assertThat(node1.check(VOLUME_ID, sharedMount, "node1", List.of("node2"), START_MS))
                .containsExactly("node2");
    }

    @Test
    void testNodeNameCannotEscapeMarkerDir(@TempDir final Path sharedMount) {
        final Path markerDir = sharedMount.resolve(SharedVolumeChecker.MARKER_DIR_NAME);

        for (final String nodeName : List.of("..", "../node", "a/b", "/etc/passwd", "node 1")) {
            final Path markerFile = SharedVolumeChecker.getMarkerFile(markerDir, nodeName);
            assertThat(markerFile.normalize().getParent())
                    .as("marker for node name '%s'", nodeName)
                    .isEqualTo(markerDir);
        }
    }

    @Test
    void testUnwritableVolumeDoesNotThrow(@TempDir final Path tempDir) throws IOException {
        // The volume path is a file, so the marker directory cannot be created.
        final Path notADirectory = Files.createFile(tempDir.resolve("file"));
        final SharedVolumeChecker node1 = new SharedVolumeChecker(Duration.ZERO, STALE_AFTER);

        assertThat(node1.check(VOLUME_ID, notADirectory, "node1", List.of("node2"), START_MS))
                .containsExactly("node2");
    }
}
