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

package stroom.pipeline.stepping.session;

import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.security.api.UserIdentity;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The session registry's keying and lifecycle rules: ids self-heal rather than fail, a changed selection
 * replaces (and closes - the on-disk capture data must go with it) the old session, one user's id can
 * never name another's session, and the idle reap reads its threshold from LIVE config - a frozen config
 * here would make maxSessionIdleTime changes silently inert until restart.
 */
class TestSteppingSessionRegistry {

    private final UserIdentity alice = Mockito.mock(UserIdentity.class, "alice");
    private final UserIdentity bob = Mockito.mock(UserIdentity.class, "bob");
    private final AtomicReference<StroomDuration> idleTime =
            new AtomicReference<>(StroomDuration.parse("PT1H"));
    private final SteppingSessionRegistry registry = new SteppingSessionRegistry(() -> {
        final SteppingConfig config = Mockito.mock(SteppingConfig.class);
        Mockito.when(config.getMaxSessionIdleTime()).thenAnswer(inv -> idleTime.get());
        return config;
    });

    private SteppingSession session(final String id, final List<Long> streams) {
        return new SteppingSession(id, streams, (metaId, request, fp, prior, running) -> null,
                s -> {
                }, sweep -> {
                }, 10);
    }

    @Test
    void testAnUnknownIdSelfHealsIntoAFreshSession() {
        // Reaped, terminated or foreign ids must not fail the step - the user pays for a re-sweep, not an
        // error, and the client adopts the new id from the response.
        final SteppingSession fresh = session("s1", List.of(1L));
        assertThat(registry.getOrCreate(alice, "unknown", List.of(1L), () -> fresh)).isSameAs(fresh);
    }

    @Test
    void testAMatchingSessionIsReturnedWithoutTheFactory() {
        final SteppingSession first = registry.getOrCreate(alice, null, List.of(1L), () -> session("s1", List.of(1L)));
        final AtomicInteger factoryCalls = new AtomicInteger();
        final SteppingSession second = registry.getOrCreate(alice, "s1", List.of(1L), () -> {
            factoryCalls.incrementAndGet();
            return session("s2", List.of(1L));
        });
        assertThat(second).isSameAs(first);
        assertThat(factoryCalls).hasValue(0);
    }

    @Test
    void testAChangedSelectionReplacesAndClosesTheOldSession() {
        // A session created for a different stream selection cannot answer; keeping it around would leak
        // its captured data on disk, so replacement must CLOSE it, not merely forget it.
        final SteppingSession old = registry.getOrCreate(alice, null, List.of(1L), () -> session("s1", List.of(1L)));
        final SteppingSession replacement =
                registry.getOrCreate(alice, "s1", List.of(2L), () -> session("s2", List.of(2L)));
        assertThat(replacement).isNotSameAs(old);
        assertThat(old.isClosed()).as("the unusable session's capture data was released").isTrue();
    }

    @Test
    void testOneUsersIdNeverReturnsAnothersSession() {
        final SteppingSession alices = registry.getOrCreate(alice, null, List.of(1L), () -> session("s1", List.of(1L)));
        final SteppingSession bobs =
                registry.getOrCreate(bob, "s1", List.of(1L), () -> session("s2", List.of(1L)));
        assertThat(bobs).as("sessions are keyed by user as well as id").isNotSameAs(alices);
        assertThat(alices.isClosed()).as("and the other user's session is untouched").isFalse();
    }

    @Test
    void testTerminateClosesTheSessionAndReportsWhetherOneExisted() {
        final SteppingSession live = registry.getOrCreate(alice, null, List.of(1L), () -> session("s1", List.of(1L)));
        assertThat(registry.terminate(alice, "nope")).isFalse();
        assertThat(registry.terminate(alice, "s1")).isTrue();
        assertThat(live.isClosed()).isTrue();
    }

    @Test
    void testReapIdleReadsTheThresholdFromLiveConfig() {
        final SteppingSession live = registry.getOrCreate(alice, null, List.of(1L), () -> session("s1", List.of(1L)));

        registry.reapIdle();
        assertThat(live.isClosed()).as("under a 1h threshold a fresh session survives").isFalse();

        // The config the provider hands out changes at runtime; the next reap must see the new value -
        // this is the pin for Provider-not-object injection (a frozen config passes every other test).
        idleTime.set(StroomDuration.parse("PT0S"));
        registry.reapIdle();
        assertThat(live.isClosed()).as("a zero threshold reaps it on the very next pass").isTrue();
    }
}
