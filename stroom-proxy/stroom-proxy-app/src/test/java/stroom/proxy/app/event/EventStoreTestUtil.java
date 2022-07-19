package stroom.proxy.app.event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class EventStoreTestUtil {

    public static String read(final Path dir,
                              final FeedKey feedKey) throws IOException {
        try (final Stream<Path> stream = Files.list(dir)) {
            final List<Path> list = stream.toList();
            assertThat(list.size()).isOne();
            final Path path = list.get(0);
            final String fileName = path.getFileName().toString();
            assertThat(fileName).endsWith(EventStoreFile.LOG_EXTENSION);
            final String prefix = feedKey.encodeKey();
            assertThat(fileName).startsWith(prefix);
            return Files.readString(path);
        }
    }
}
