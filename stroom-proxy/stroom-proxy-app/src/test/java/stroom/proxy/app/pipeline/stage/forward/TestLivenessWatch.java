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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.execution.Loop;
import stroom.proxy.app.execution.Phase;
import stroom.proxy.app.execution.WorkRegistry;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.NullDestination;
import stroom.proxy.app.handler.RecordingDestination;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.local.LocalFileGroupQueue;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.app.pipeline.store.filesystem.FilesystemFileStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestLivenessWatch extends StroomUnitTest {

    @Test
    void testAFailingCheckPausesTheLoopAndAPassingOneResumesIt() {
        final Loop loop = Mockito.mock(Loop.class);
        final AtomicBoolean live = new AtomicBoolean(false);
        final LivenessWatch watch = new LivenessWatch("dest", () -> {
            if (!live.get()) {
                throw new Exception("down");
            }
        }, loop);

        assertThat(watch.lastLive()).isNull();
        watch.run();
        assertThat(watch.lastLive()).isFalse();
        Mockito.verify(loop).pause();
        Mockito.verify(loop, Mockito.never()).resume();

        watch.run();
        Mockito.verify(loop, Mockito.times(2)).pause();

        live.set(true);
        watch.run();
        assertThat(watch.lastLive()).isTrue();
        Mockito.verify(loop).resume();
    }

    @Test
    void testAPausedLoopHoldsNoClaim() throws Exception {
        final Path root = getCurrentTestDir();
        final FilesystemFileStore store = new FilesystemFileStore("aggregateStore", root.resolve("store"));
        final LocalFileGroupQueue queue = new LocalFileGroupQueue(
                "forwardingInput", Files.createDirectories(root.resolve("in")));
        final List<Path> delivered = new ArrayList<>();
        final ForwardStage stage = new ForwardStage(
                new FileStoreRegistry(List.of(store)),
                new RecordingDestination(delivered),
                new GiveUp(new NullDestination()),
                new ForwardBounds(Duration.ofDays(7), Duration.ZERO, 1, Duration.ZERO),
                () -> false);
        final FileGroupQueueWorker worker = new FileGroupQueueWorker(queue, stage);
        final WorkRegistry registry = new WorkRegistry();
        final Loop loop = registry.loop("stage-forward", Phase.FORWARD, 2, () -> worker.processNext().loopOutcome());

        final AtomicBoolean live = new AtomicBoolean(false);
        final LivenessWatch watch = new LivenessWatch("dest", () -> {
            if (!live.get()) {
                throw new Exception("down");
            }
        }, loop);
        watch.run();
        assertThat(loop.isPaused()).isTrue();

        registry.start();
        try {
            publish(store, queue);
            Thread.sleep(500);
            assertThat(queue.getApproximateInFlightCount()).as("paused: nothing claimed").isZero();
            assertThat(queue.getApproximatePendingCount()).isEqualTo(1);
            assertThat(delivered).isEmpty();

            live.set(true);
            watch.run();
            assertThat(loop.isPaused()).isFalse();
            final long deadline = System.currentTimeMillis() + 10_000;
            while (delivered.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(delivered).as("resumed: delivered").hasSize(1);
        } finally {
            registry.stop();
            queue.close();
        }
    }

    private static void publish(final FilesystemFileStore store, final LocalFileGroupQueue queue) throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = store.newWrite()) {
            final FileGroup group = new FileGroup(write.getPath());
            final AttributeMap meta = new AttributeMap();
            AttributeMapUtil.addFeedAndType(meta, "TEST_FEED", "Raw Events");
            AttributeMapUtil.write(meta, group.getMeta());
            Files.writeString(group.getZip(), "zip");
            Files.writeString(group.getEntries(), "");
            location = write.commit();
        }
        queue.publish(FileGroupQueueMessage.create(
                location, "TEST_FEED", "Raw Events", "aggregate", "node", null, Map.of()));
    }
}
