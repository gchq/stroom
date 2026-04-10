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

package stroom.util.shared;

import java.util.Comparator;
import java.util.Objects;

/**
 * A wrapper to hold a feed name and a stream type.
 * Can be used to pass these two values around or as a map/cache key.
 * Hashcode is lazily computed and held on the object for future use.
 * Both feed and type are nullable.
 */
public final class FeedKey implements Comparable<FeedKey> {

    private static final Comparator<FeedKey> COMPARATOR = CompareUtil.getNullSafeComparator(FeedKey::feed)
            .thenComparing(CompareUtil.getNullSafeComparator(FeedKey::type));

    private static final FeedKey EMPTY = new FeedKey(null, null);

    private final String feed;
    private final String type;
    private int hash = 0;
    private boolean hashIsZero = false;

    private FeedKey(final String feed, final String type) {
        this.feed = feed;
        this.type = type;
    }

    public static FeedKey of(final String feed, final String type) {
        // Don't store empty strings for consistency
        final String feed2 = emptyStringAsNull(feed);
        final String type2 = emptyStringAsNull(type);
        if (feed2 == null && type2 == null) {
            return EMPTY;
        } else {
            return new FeedKey(feed2, type2);
        }
    }

    public static FeedKey ofFeed(final String feed) {
        return of(feed, null);
    }

    public static FeedKey empty() {
        return EMPTY;
    }

    private static String emptyStringAsNull(final String str) {
        if (str == null || str.isEmpty()) {
            return null;
        } else {
            return str;
        }
    }

    @Override
    public String toString() {
        return NullSafe.string(feed) + ":" + NullSafe.string(type);
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

    @Override
    public int compareTo(final FeedKey o) {
        Objects.requireNonNull(o); // Enforcing the contract of compareTo
        return COMPARATOR.compare(this, o);
    }
}
