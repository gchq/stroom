/*
 * Copyright 2016-2025 Crown Copyright
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
        final FeedKey that = (FeedKey) obj;
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

        // Fewer types than feeds so key on that first to reduce number of child maps
        private final Map<String, Map<String, FeedKey>> typeToFeedToFeedKeyMap = new HashMap<>();

        private FeedKeyInterner() {
        }

        public FeedKey intern(final String feed, final String type) {
            final Map<String, FeedKey> feedToFeedKeyMap = typeToFeedToFeedKeyMap.computeIfAbsent(
                    type, k -> new HashMap<>());
            return feedToFeedKeyMap.computeIfAbsent(feed, aFeed -> FeedKey.of(aFeed, type));
        }

        public FeedKey intern(final FeedKey feedKey) {
            if (feedKey != null) {
                final FeedKey prevVal = typeToFeedToFeedKeyMap.computeIfAbsent(
                                feedKey.type, k -> new HashMap<>())
                        .putIfAbsent(feedKey.feed, feedKey);
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
