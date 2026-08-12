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

package stroom.security.identity.token;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestRevokedJtiCache {

    @Mock
    private OAuthTokenDao oAuthTokenDao;

    private RevokedJtiCache cache;

    @BeforeEach
    void setUp() {
        cache = new RevokedJtiCache(oAuthTokenDao);
    }

    @Test
    void revokedJtiIsReportedAsRevoked() {
        when(oAuthTokenDao.fetchRevokedJtis(anyLong())).thenReturn(Set.of("bad-jti"));

        assertThat(cache.isRevoked("bad-jti")).isTrue();
    }

    @Test
    void unknownJtiIsNotRevoked() {
        when(oAuthTokenDao.fetchRevokedJtis(anyLong())).thenReturn(Set.of("bad-jti"));

        assertThat(cache.isRevoked("some-other-jti")).isFalse();
    }

    @Test
    void nullJtiIsNotRevokedAndIsNotLookedUp() {
        assertThat(cache.isRevoked(null)).isFalse();

        // No point loading a denylist to answer a question with no key.
        verify(oAuthTokenDao, never()).fetchRevokedJtis(anyLong());
    }

    @Test
    void theDenylistIsLoadedOnceAndThenAnsweredFromMemory() {
        // The whole point: this sits on the hot path of every authenticated request, so repeated checks must
        // not each cost a database read.
        when(oAuthTokenDao.fetchRevokedJtis(anyLong())).thenReturn(Set.of("bad-jti"));

        for (int i = 0; i < 50; i++) {
            cache.isRevoked("bad-jti");
            cache.isRevoked("good-jti");
        }

        verify(oAuthTokenDao, times(1)).fetchRevokedJtis(anyLong());
    }

    @Test
    void invalidatingForcesAReloadSoARevokeTakesEffectImmediately() {
        when(oAuthTokenDao.fetchRevokedJtis(anyLong()))
                .thenReturn(Set.of())
                .thenReturn(Set.of("newly-revoked"));

        assertThat(cache.isRevoked("newly-revoked")).isFalse();

        // What the revocation fan-out will call on every node.
        cache.invalidate();

        assertThat(cache.isRevoked("newly-revoked")).isTrue();
        verify(oAuthTokenDao, times(2)).fetchRevokedJtis(anyLong());
    }

    @Test
    void anEmptyDenylistRevokesNothing() {
        // Cold start on a system where nothing has ever been revoked, which is the overwhelmingly common
        // case and must not reject anything.
        when(oAuthTokenDao.fetchRevokedJtis(anyLong())).thenReturn(Set.of());

        assertThat(cache.isRevoked("any-jti")).isFalse();
    }
}
