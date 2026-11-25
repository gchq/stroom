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

import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// How much a feed holds, said before a promotion's filter can run (decided by the user, 2026-11-25).
class TestFeedVolumes {

    private static final long FIRST = Instant.parse("2026-01-02T03:04:05Z").toEpochMilli();
    private static final long LAST = Instant.parse("2026-11-20T00:00:00Z").toEpochMilli();

    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final FeedVolumes volumes = new FeedVolumes(() -> metaService);

    @Test
    void aFeedsStreamsAreCountedAndSizedWithTheirFirstAndLastDates() {
        summary(3);
        rows(List.of(row(1, "1024"), row(2, "1024"), row(3, "2048")));

        final FeedVolumes.Volume volume = volumes.volume("ACME-A", "Raw Events");

        assertThat(volume.streams()).isEqualTo(3);
        assertThat(volume.bytes()).isEqualTo(4096L);
        assertThat(volume.estimated()).isFalse();
        assertThat(volume.said()).isEqualTo("ACME-A (Raw Events): 3 streams, 4K, created "
                                            + "2026-01-02T03:04:05.000Z to 2026-11-20T00:00:00.000Z");
    }

    @Test
    void pastTheStreamsSizedTheSizeIsEstimatedAndSaidSo() {
        // A dormant feed with a long history: 5,000 streams, the newest 1,000 read for their size.
        summary(5_000);
        final List<MetaRow> rows = new ArrayList<>();
        for (int i = 0; i < FeedVolumes.MAX_SIZED; i++) {
            rows.add(row(i, "1024"));
        }
        rows(rows);

        final FeedVolumes.Volume volume = volumes.volume("ACME-B", "Raw Events");

        assertThat(volume.estimated()).isTrue();
        assertThat(volume.bytes()).isEqualTo(5_000L * 1024);
        assertThat(volume.said()).contains("5000 streams, about 4.9M");
    }

    @Test
    void aFeedWithNoStreamsOrUnreadableOrUnsizedIsSaidSo() {
        summary(0);
        assertThat(volumes.volume("ACME-C", "Raw Events").said()).isEqualTo("ACME-C (Raw Events): no streams yet");

        summary(1);
        rows(List.of(new MetaRow(Meta.builder().id(1).build(), null, Map.of())));
        assertThat(volumes.volume("ACME-C", "Raw Events").said()).startsWith("ACME-C (Raw Events): 1 stream, size "
                                                                             + "not known");

        Mockito.when(metaService.getSelectionSummary(any())).thenThrow(new IllegalStateException("no"));
        assertThat(volumes.volume("ACME-C", "Raw Events").said()).isEqualTo("ACME-C (Raw Events): its streams "
                                                                            + "couldn't be counted");
    }

    private void summary(final long count) {
        Mockito.when(metaService.getSelectionSummary(any())).thenReturn(new SelectionSummary(count, 1, Set.of(),
                1, Set.of(), 0, 0, 1, Set.of(), new Range<>(FIRST, LAST)));
    }

    private void rows(final List<MetaRow> rows) {
        Mockito.when(metaService.findRows(any())).thenReturn(new ResultPage<>(rows));
    }

    private static MetaRow row(final long id, final String size) {
        return new MetaRow(Meta.builder().id(id).build(), null, Map.of(MetaFields.FILE_SIZE.getFldName(), size));
    }
}
