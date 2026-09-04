/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.meta.shared.Meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * A part that fails to transfer must fail the publish.
 *
 * <p>The parts hold the only copy of that stream's data, and a stream recorded as successful is never
 * reprocessed — so swallowing the failure loses the data with no way to recover it.
 */
class TestDefaultBatchDestination {

    private static final PartDestination SUCCEEDS = (part, meta) -> true;
    private static final PartDestination FAILS = (part, meta) -> false;

    @Test
    void publishThrows_whenAPartFailsToTransfer(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = Files.createDirectory(tempDir.resolve("writer"));

        assertThatThrownBy(() -> new DefaultBatchDestination().publish(batch(writerDir, FAILS)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to transfer 1 of 1");
    }

    /** One unreachable destination must not strand the parts that could have been sent. */
    @Test
    void publishAttemptsEveryPart_evenAfterOneFails(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = Files.createDirectory(tempDir.resolve("writer"));
        final AtomicInteger attempts = new AtomicInteger();
        final PartDestination counting = (part, meta) -> {
            attempts.incrementAndGet();
            return true;
        };

        assertThatThrownBy(() -> new DefaultBatchDestination()
                .publish(batch(writerDir, FAILS, counting, counting)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to transfer 1 of 3");

        assertThat(attempts.get()).as("both later parts still attempted").isEqualTo(2);
    }

    /** The writer dir is the only remaining copy of what did not transfer, so it must survive. */
    @Test
    void publishRetainsTheWriterDir_whenAPartFailed(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = Files.createDirectory(tempDir.resolve("writer"));

        assertThatThrownBy(() -> new DefaultBatchDestination()
                .publish(batch(writerDir, SUCCEEDS, FAILS)))
                .isInstanceOf(IOException.class);

        assertThat(writerDir).as("retained for the retry / operator inspection").exists();
    }

    @Test
    void publishDeletesTheWriterDir_whenEveryPartSucceeded(@TempDir final Path tempDir) throws IOException {
        final Path writerDir = Files.createDirectory(tempDir.resolve("writer"));

        new DefaultBatchDestination().publish(batch(writerDir, SUCCEEDS, SUCCEEDS));

        assertThat(writerDir).doesNotExist();
    }

    // DefaultBatchDestination reads only the destination off each part, so the rest can be null.
    private static WrittenBatch batch(final Path writerDir, final PartDestination... destinations) {
        return new WrittenBatch(
                writerDir,
                mock(Meta.class),
                List.of(destinations).stream()
                        .map(d -> new WrittenPart(null, null, -1, false, d))
                        .toList());
    }
}
