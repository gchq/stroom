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

package stroom.mcp.impl.tools;

import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/// The users' agreements to survey a feed fewer streams a call than the default: an agreement holds for that user, feed
/// and count for four hours, so later calls go on unasked. Kept in this node's memory.
@Singleton
class FewerStreams {

    /// How long an agreement holds.
    static final Duration TTL = Duration.ofHours(4);
    /// The most agreements kept: the oldest are forgotten first.
    static final int MAX_AGREEMENTS = 10_000;

    private final SecurityContext securityContext;
    private final LongSupplier clock;
    private final Map<String, Agreed> agreed = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Agreed> eldest) {
            return size() > MAX_AGREEMENTS;
        }
    });

    /// @param securityContext Says who the user is.
    @Inject
    FewerStreams(final SecurityContext securityContext) {
        this(securityContext, System::currentTimeMillis);
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param securityContext Says who the user is.
    /// @param clock           The time now, in milliseconds.
    FewerStreams(final SecurityContext securityContext, final LongSupplier clock) {
        this.securityContext = securityContext;
        this.clock = clock;
    }

    /// @param feed  A feed.
    /// @param count Streams a call.
    /// @return True if the user agreed to survey the feed this many streams a call within [#TTL].
    boolean agreed(final String feed, final int count) {
        final Agreed found = agreed.get(key(feed));
        return found != null && found.count() == count && clock.getAsLong() - found.atMs() < TTL.toMillis();
    }

    /// Remembers that the user agreed to survey the feed this many streams a call.
    ///
    /// @param feed  A feed.
    /// @param count Streams a call.
    void agree(final String feed, final int count) {
        agreed.put(key(feed), new Agreed(count, clock.getAsLong()));
    }

    private String key(final String feed) {
        String user;
        try {
            user = NullSafe.get(securityContext.getUserRef(), UserRef::getUuid);
        } catch (final RuntimeException e) {
            user = null;
        }
        return NullSafe.string(user) + "\n" + NullSafe.string(feed).toLowerCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    private record Agreed(int count, long atMs) {

    }
}
