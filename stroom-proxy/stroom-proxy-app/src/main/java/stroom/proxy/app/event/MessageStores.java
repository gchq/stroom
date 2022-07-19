package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class MessageStores implements EventConsumer {

    private static final Pattern PATTERN = Pattern.compile("[^A-Za-z0-9]");

    private final Path dir;
    private final Cache<FeedKey, MessageStore> cache;

    public MessageStores(final Path dir) {
        this.dir = dir;
        final Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder();
        cacheBuilder.maximumSize(100);
        this.cache = cacheBuilder.build();
    }

    @Override
    public void consume(final AttributeMap attributeMap, final Consumer<OutputStream> consumer) {
        final String feed = attributeMap.get("Feed");
        final String type = attributeMap.get("type");
        final FeedKey feedKey = new FeedKey(feed, type);
        final MessageStore messageStore = cache.get(feedKey, k -> {
            try {
                final String fileName = createFileName(k);
                final Path file = dir.resolve(fileName);
                return new MessageStore(file);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        messageStore.consume(attributeMap, consumer);
    }

    private String createFileName(final FeedKey feedKey) {
        final String feed = simplify(feedKey.feed());
        final String type = simplify(feedKey.type());
        return feed + "=" + type + ".dat";
    }

    private String simplify(final String string) {
        if (string == null) {
            return "";
        }
        return PATTERN.matcher(string).replaceAll("_");
    }
}
