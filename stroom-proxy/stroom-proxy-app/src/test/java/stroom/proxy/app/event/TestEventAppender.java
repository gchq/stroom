package stroom.proxy.app.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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
}
