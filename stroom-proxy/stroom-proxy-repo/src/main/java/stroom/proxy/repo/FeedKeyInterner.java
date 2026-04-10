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

package stroom.proxy.repo;


import stroom.util.shared.FeedKey;

import com.github.benmanes.caffeine.cache.Interner;

import java.util.function.Consumer;

/**
 * Allows re-use of {@link FeedKey} instances.
 * Main aim is to reduce object instances for the same feed+type to save memory.
 * {@link FeedKey} instances are held with weak references so will be GC'd when no
 * longer used elsewhere in the code.
 * <p>Thread safe.</p>
 */
public class FeedKeyInterner {

    private final Interner<FeedKey> interner;

    public FeedKeyInterner() {
        this.interner = Interner.newWeakInterner();
    }

    /**
     * Creates an {@link FeedKeyInterner} to intern FeedKey instances to reduce the
     * total number of unique {@link FeedKey} instances in memory. Uses default capacity
     * to allow for 12 feeds before resizing.
     * <p></p>
     */
    public static FeedKeyInterner create() {
        return new FeedKeyInterner();
    }

    public FeedKey intern(final String feed, final String type) {
        return intern(FeedKey.of(feed, type));
    }

    /**
     * @param feedKey The {@link FeedKey} to intern.
     * @return The canonical {@link FeedKey}.
     */
    public FeedKey intern(final FeedKey feedKey) {
        if (feedKey != null) {
            return interner.intern(feedKey);
        } else {
            return null;
        }
    }

    /**
     * @param feedKey         The {@link FeedKey} to intern.
     * @param feedKeyConsumer Called with the already interned {@link FeedKey} <strong>only </strong>
     *                        if it is not the same instance as the {@code feedKey} argument.
     */
    public void consumeIfNotInterned(final FeedKey feedKey,
                                     final Consumer<FeedKey> feedKeyConsumer) {
        final FeedKey internedFeedKey = intern(feedKey);
        if (feedKey != internedFeedKey) {
            feedKeyConsumer.accept(internedFeedKey);
        }
    }
}
