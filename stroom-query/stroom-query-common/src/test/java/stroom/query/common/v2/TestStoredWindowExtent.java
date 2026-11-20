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

package stroom.query.common.v2;

import stroom.query.common.v2.StoredWindowExtent.Snapshot;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestStoredWindowExtent {

    @Test
    void newStoreIsReadThroughOnce() {
        final StoredWindowExtent extent = new StoredWindowExtent();

        // A store may hold rows when it is opened.
        final Snapshot first = extent.snapshot();
        assertThat(extent.needsScan(first)).isTrue();
        extent.scanned(first, 10, 20);

        assertThat(extent.needsScan(extent.snapshot())).isFalse();
        assertThat(extent.getFirst(first)).isEqualTo(10);
        assertThat(extent.getLast(first)).isEqualTo(20);
    }

    @Test
    void committedRowsWidenTheExtentWithNoScan() {
        final StoredWindowExtent extent = scannedEmpty();

        extent.add(realRow(30));
        extent.add(realRow(10));
        // A copy of a row from another window, and a row below the top level.
        extent.add(new LmdbKV(null, buffer(), buffer(), null));
        extent.committed(false);

        final Snapshot snapshot = extent.snapshot();
        assertThat(extent.needsScan(snapshot)).isFalse();
        assertThat(extent.getFirst(snapshot)).isEqualTo(10);
        assertThat(extent.getLast(snapshot)).isEqualTo(30);
    }

    @Test
    void rowsThatAreNotCommittedAreNotSeen() {
        final StoredWindowExtent extent = scannedEmpty();

        extent.add(realRow(10));

        final Snapshot snapshot = extent.snapshot();
        assertThat(extent.getFirst(snapshot)).isGreaterThan(extent.getLast(snapshot));
    }

    @Test
    void readDoesNotSeeRowsCommittedAfterItBegan() {
        final StoredWindowExtent extent = scannedEmpty();
        extent.add(realRow(10));
        extent.committed(false);

        final Snapshot before = extent.snapshot();
        extent.add(realRow(50));
        extent.committed(false);

        assertThat(extent.getLast(before)).isEqualTo(10);
        assertThat(extent.getLast(extent.snapshot())).isEqualTo(50);
    }

    @Test
    void deletedRowsMakeTheStoreBeReadThroughAgain() {
        final StoredWindowExtent extent = scannedEmpty();
        extent.add(realRow(10));
        extent.add(realRow(30));
        extent.committed(false);

        extent.committed(true);

        final Snapshot snapshot = extent.snapshot();
        assertThat(extent.needsScan(snapshot)).isTrue();
        // The first window was deleted, and reading through the store finds that.
        extent.scanned(snapshot, 30, 30);
        assertThat(extent.getFirst(snapshot)).isEqualTo(30);
        assertThat(extent.getLast(snapshot)).isEqualTo(30);
    }

    @Test
    void commitThatDeletesNothingNeedsNoScan() {
        final StoredWindowExtent extent = scannedEmpty();

        extent.committed(false);

        assertThat(extent.needsScan(extent.snapshot())).isFalse();
    }

    @Test
    void rowFromAnotherNodeMakesTheStoreBeReadThroughAgain() {
        final StoredWindowExtent extent = scannedEmpty();

        extent.add(new LmdbKV(null, buffer(), buffer()));
        extent.committed(false);

        assertThat(extent.needsScan(extent.snapshot())).isTrue();
    }

    @Test
    void scanOfAnOlderReadIsNotTakenAsCoveringALaterDelete() {
        final StoredWindowExtent extent = new StoredWindowExtent();

        final Snapshot before = extent.snapshot();
        extent.committed(true);
        extent.scanned(before, 10, 20);

        assertThat(extent.needsScan(extent.snapshot())).isTrue();
    }

    @Test
    void readThatBeginsDuringACommitIsNotSettled() {
        final StoredWindowExtent extent = scannedEmpty();

        // The rows are committed after the read took its first snapshot, so the read may see them.
        final Snapshot before = extent.snapshot();
        extent.beginCommit();
        assertThat(StoredWindowExtent.isSettled(before, extent.snapshot())).isFalse();
        extent.committed(false);
        assertThat(StoredWindowExtent.isSettled(before, extent.snapshot())).isFalse();

        // The commit has begun, but the windows of its rows are not yet noted.
        extent.beginCommit();
        final Snapshot during = extent.snapshot();
        assertThat(StoredWindowExtent.isSettled(during, extent.snapshot())).isFalse();
        extent.committed(false);

        final Snapshot quiet = extent.snapshot();
        assertThat(StoredWindowExtent.isSettled(quiet, extent.snapshot())).isTrue();
    }

    private static StoredWindowExtent scannedEmpty() {
        final StoredWindowExtent extent = new StoredWindowExtent();
        extent.scanned(extent.snapshot(), Long.MAX_VALUE, Long.MIN_VALUE);
        return extent;
    }

    private static LmdbKV realRow(final long windowKey) {
        return new LmdbKV(null, buffer(), buffer(), windowKey);
    }

    private static ByteBuffer buffer() {
        return ByteBuffer.allocate(1);
    }
}
