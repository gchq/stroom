/*
 * Copyright 2022 Crown Copyright
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class TestEventAppender {

    @Test
    void test() throws IOException {
        final Path dir = Files.createTempDirectory("stroom");

        final FeedKey feedKey = new FeedKey("Test", "Raw Events");
        final Instant now = Instant.now();
        final Path file = EventStoreFile.createNew(dir, feedKey, now);
        final EventAppender eventAppender = new EventAppender(file, now, new EventStoreConfig());
        final StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            eventAppender.write("test\n".getBytes(StandardCharsets.UTF_8));
            expected.append("test\n");
        }
        eventAppender.close();
        assertThat(EventStoreTestUtil.read(dir, feedKey)).isEqualTo(expected.toString());
    }

    /**
     * Guards the idempotence of close() after a <em>clean</em> close.
     * <p>
     * <strong>This does not pin M75.</strong> M75 is about a close that fails part way through its
     * flush, which used to leave the field set so a second close retried a stream that had already
     * failed. Injecting a close failure would mean making the appender's {@link java.io.OutputStream}
     * settable purely for a test, and that is not worth it - so the failing-close path is fixed but
     * unpinned, and this test only holds the surrounding behaviour still.
     * </p>
     */
    @Test
    void testCloseIsIdempotent() throws IOException {
        final Path dir = Files.createTempDirectory("stroom");
        final Instant now = Instant.now();
        final Path file = EventStoreFile.createNew(dir, new FeedKey("Test", "Raw Events"), now);
        final EventAppender appender = new EventAppender(file, now, new EventStoreConfig());
        appender.write("some events".getBytes(StandardCharsets.UTF_8));

        appender.close();

        assertThatNoException()
                .as("a second close must be a no-op, not a retry of a stream already closed")
                .isThrownBy(appender::close);
    }
}
