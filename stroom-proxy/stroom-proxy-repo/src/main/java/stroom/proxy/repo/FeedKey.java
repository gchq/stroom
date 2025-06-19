package stroom.proxy.repo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class FeedKey {

    private final String feed;
    private final String type;
    private int hash = 0;
    private boolean hashIsZero = false;

    public FeedKey(final String feed, final String type) {
        this.feed = feed;
        this.type = type;
    }

    public static FeedKey of(final String feed, final String type) {
        return new FeedKey(feed, type);
    }

    @Override
    public String toString() {
        return feed + ":" + type;
    }

    public String feed() {
        return feed;
    }

    public String type() {
        return type;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final var that = (FeedKey) obj;
        return Objects.equals(this.feed, that.feed) &&
               Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        // Lazy hashCode caching as this is used as a map key.
        // Borrows pattern from String.hashCode()
        int h = hash;
        if (h == 0 && !hashIsZero) {
            h = Objects.hash(feed, type);
            if (h == 0) {
                hashIsZero = true;
            } else {
                hash = h;
            }
        }
        return h;
    }

    /**
     * Creates an {@link FeedKeyInterner} to intern FeedKey instances to reduce the
     * total number of unique {@link FeedKey} instances in memory.
     * <p>Not thread safe. Only for local use.</p>
     */
    public static FeedKeyInterner createInterner() {
        return new FeedKeyInterner();
    }


    // --------------------------------------------------------------------------------


    /**
     * Allows re-use of {@link FeedKey} instances by a thread.
     * Not thread safe.
     */
    public static class FeedKeyInterner {

        private final Map<FeedKey, FeedKey> map = new HashMap<>();

        private FeedKeyInterner() {
        }

        public FeedKey intern(final String feed, final String type) {
            return intern(FeedKey.of(feed, type));
        }

        public FeedKey intern(final FeedKey feedKey) {
            if (feedKey != null) {
                final FeedKey prevVal = map.putIfAbsent(feedKey, feedKey);
                return prevVal != null
                        ? prevVal
                        : feedKey;
            } else {
                return null;
            }
        }

        /**
         * @param feedKey         The {@link FeedKey} to intern.
         * @param feedKeyConsumer Called with the interned {@link FeedKey} only if it is
         *                        not the same instance as the supplied feedKey.
         */
        public void consumeInterned(final FeedKey feedKey,
                                    final Consumer<FeedKey> feedKeyConsumer) {
            final FeedKey internedFeedKey = intern(feedKey);
            if (feedKey != internedFeedKey) {
                feedKeyConsumer.accept(internedFeedKey);
            }
        }
    }
}
