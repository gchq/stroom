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

package stroom.index.impl;

import stroom.security.mock.MockSecurityContext;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;

class TestIndexShardStreamRecovery {

    @Test
    void testExec_takesLapsedLeasesWithTheConfiguredGraceAndRetention() {
        final IndexShardDao indexShardDao = Mockito.mock(IndexShardDao.class);
        final IndexWriterConfig config = new IndexWriterConfig(
                null,
                null,
                null,
                null,
                StroomDuration.ofMinutes(20),
                null,
                null,
                StroomDuration.ofMinutes(7));
        Mockito.when(indexShardDao.releaseLapsedLeases(anyLong(), anyLong())).thenReturn(2);
        final IndexShardStreamRecovery recovery = new IndexShardStreamRecovery(
                indexShardDao, MockSecurityContext.getInstance(), () -> config);

        recovery.exec();

        Mockito.verify(indexShardDao).releaseLapsedLeases(7 * 60_000L, 20 * 60_000L);
    }

    @Test
    void testExec_failureIsLoggedNotThrown() {
        final IndexShardDao indexShardDao = Mockito.mock(IndexShardDao.class);
        Mockito.when(indexShardDao.releaseLapsedLeases(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("no database"));
        final IndexShardStreamRecovery recovery = new IndexShardStreamRecovery(
                indexShardDao, MockSecurityContext.getInstance(), IndexWriterConfig::new);

        // The job runs again in a minute.
        assertThatCode(recovery::exec).doesNotThrowAnyException();
    }
}
