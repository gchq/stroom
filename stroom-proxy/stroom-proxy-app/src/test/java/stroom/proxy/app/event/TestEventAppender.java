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

package stroom.proxy.app.event;

import stroom.proxy.app.handler.Durability;
import stroom.util.shared.FeedKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestEventAppender {

    @TempDir
    Path dir;

    @Test
    void testTheFileIsOpenUntilCloseRenamesIt() throws IOException {
        final FeedKey feedKey = FeedKey.of("Test", "Raw Events");
        final Path file = EventStoreFile.createNew(dir, feedKey, Instant.now());
        final EventAppender appender = appender(file, Durability.FULL);

        final StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            appender.write("test\n".getBytes(StandardCharsets.UTF_8));
            expected.append("test\n");
        }
        assertThat(file).as("nothing ending in .log exists while the file is being written").doesNotExist();
        assertThat(EventAppender.openFileOf(file)).exists();

        appender.close();

        assertThat(EventAppender.openFileOf(file)).doesNotExist();
        assertThat(EventStoreTestUtil.read(dir, feedKey)).isEqualTo(expected.toString());
        assertThatNoException().as("a second close is a no-op").isThrownBy(appender::close);
        assertThatThrownBy(() -> appender.write("late\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testAnAppenderNeverWrittenToLeavesNoFile() throws IOException {
        final Path file = EventStoreFile.createNew(dir, FeedKey.of("Test", "Raw Events"), Instant.now());
        final EventAppender appender = appender(file, Durability.FULL);

        appender.close();

        assertThat(file).doesNotExist();
        assertThat(EventAppender.openFileOf(file)).doesNotExist();
    }

    @Test
    void testShouldRollByAgeCountAndSize() throws IOException {
        final Path file = EventStoreFile.createNew(dir, FeedKey.of("Test", "Raw Events"), Instant.now());
        final EventAppender byCount = new EventAppender(
                file, Instant.now(), Duration.ofDays(1), 2, 1_000, Durability.FULL);
        assertThat(byCount.shouldRoll(1)).isFalse();
        byCount.write("a\n".getBytes(StandardCharsets.UTF_8));
        byCount.write("b\n".getBytes(StandardCharsets.UTF_8));
        assertThat(byCount.shouldRoll(0)).as("at the count").isTrue();
        byCount.close();

        final Path file2 = EventStoreFile.createNew(dir, FeedKey.of("Test2", "Raw Events"), Instant.now());
        final EventAppender bySize = new EventAppender(
                file2, Instant.now(), Duration.ofDays(1), 100, 5, Durability.FULL);
        bySize.write("abc\n".getBytes(StandardCharsets.UTF_8));
        assertThat(bySize.shouldRoll(1)).isFalse();
        assertThat(bySize.shouldRoll(2)).as("a write that would exceed the size").isTrue();
        bySize.close();

        final Path file3 = EventStoreFile.createNew(dir, FeedKey.of("Test3", "Raw Events"), Instant.now());
        final EventAppender byAge = new EventAppender(
                file3, Instant.now().minusSeconds(10), Duration.ofSeconds(5), 100, 1_000, Durability.FULL);
        assertThat(byAge.shouldRoll(0)).as("older than maxAge, even with nothing written").isTrue();
    }

    /**
     * The acknowledgement must survive a power cut, so the handle carries {@code DSYNC} when the
     * configured durability calls for it, and a proxy that rests on an ordered mount does not pay.
     */
    @Test
    void testAnAcknowledgedEventIsForcedToDiskUnlessDurabilitySaysOtherwise() throws IOException {
        for (final Durability durability : Durability.values()) {
            final Path file = dir.resolve(durability.name().toLowerCase() + ".log");
            final EventAppender appender = appender(file, durability);
            appender.write("event\n".getBytes(StandardCharsets.UTF_8));
            appender.close();
            assertThat(file).hasContent("event");
        }
        assertThat(Durability.FULL.forcesEvents()).isTrue();
        assertThat(Durability.QUEUE_ONLY.forcesEvents())
                .as("an event is small and its acknowledgement is a promise, like a queue message")
                .isTrue();
        assertThat(Durability.FILESYSTEM.forcesEvents()).isFalse();
    }

    private static EventAppender appender(final Path file, final Durability durability) {
        return new EventAppender(file, Instant.now(), Duration.ofDays(1), Long.MAX_VALUE, Long.MAX_VALUE, durability);
    }
}
