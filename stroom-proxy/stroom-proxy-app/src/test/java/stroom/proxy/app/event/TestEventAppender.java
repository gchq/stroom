package stroom.proxy.app.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEventAppender {

    @Test
    void test() throws IOException {
        final Path dir = Files.createTempDirectory("stroom");

        final FeedKey feedKey = new FeedKey("Test", "Raw Events");
        final EventAppender eventAppender = new EventAppender(dir, feedKey, new EventStoreConfig());
        final StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            eventAppender.write("test\n".getBytes(StandardCharsets.UTF_8));
            expected.append("test\n");
        }
        eventAppender.close();
        assertThat(EventStoreTestUtil.read(dir, feedKey, EventStoreFile.TEMP_EXTENSION))
                .isEqualTo(expected.toString());
        eventAppender.roll();
        assertThat(EventStoreTestUtil.read(dir, feedKey, EventStoreFile.LOG_EXTENSION))
                .isEqualTo(expected.toString());
    }
}
