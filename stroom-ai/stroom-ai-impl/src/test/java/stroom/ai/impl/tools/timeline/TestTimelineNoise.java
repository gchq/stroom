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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelineNoise.Exclusion;
import stroom.ai.impl.tools.timeline.TimelineNoise.Noise;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Noisy patterns found by their signals alone, proposed, and left out once the user agrees, kept for later runs.
class TestTimelineNoise {

    private static final List<String> NAMES = List.of("Proxy", "Endpoint");
    private static final long CAP = 30 * 60_000L;

    private final List<Event> events = new ArrayList<>();
    private final Map<String, Classified> classified = new LinkedHashMap<>();
    private int ids;
    @TempDir
    private Path tempDir;

    @Test
    void aHeartbeatAtRegularIntervalsWithNoOneThereIsNoiseAndTheUsersOwnBrowsingIsnt() {
        heartbeat(120);
        browsing();

        final List<Noise> noise = TimelineNoise.find(events, classified, NAMES, List.of(), CAP);

        assertThat(noise).singleElement().satisfies(found -> {
            assertThat(found.id()).isEqualTo("noise-1");
            assertThat(found.source()).isEqualTo("Proxy");
            assertThat(found.type()).isEqualTo("Beacon-Ping");
            assertThat(found.count()).isEqualTo(120);
            assertThat(found.signals()).contains("at regular intervals", "near-identical repeats",
                    "background requests with no page or title change");
            assertThat(found.examples()).hasSize(TimelineNoise.EXAMPLES).allMatch(example -> example.contains("("));
            assertThat(found.said()).startsWith("Proxy: Beacon-Ping (120 events, ");
        });
    }

    @Test
    void theUsersOwnRepeatedSavesAndCopiesInFocusAreNeverNoise() {
        // Copies and saves of one document every half minute or so, while its window is in focus.
        events.add(new Event(at("09:00"), 1, "focus", Role.FOCUS, null, "ws-01", "WINWORD.EXE", "Q3.docx", null,
                null, null, null, null));
        for (int i = 0; i < 60; i++) {
            add(at("09:01") + i * 23_000L + (i % 5) * 4_000L, 1, i % 2 == 0
                    ? "Copy-Clipboard"
                    : "Save-File", "ws-01", i % 2 == 0
                    ? null
                    : "C:\\docs\\Q3.docx", Map.of());
        }

        assertThat(TimelineNoise.find(events, classified, NAMES, List.of(), CAP)).isEmpty();
    }

    @Test
    void aScriptDownloadingManyDocumentsAtASteadyPaceIsNeverNoise() {
        // Regular and with no one there, but each a different document: what an analyst needs to see.
        for (int i = 0; i < 60; i++) {
            add(at("22:00") + i * 20_000L, 0, "Download-File", "ws-01", "sites/finance/Report " + i + ".xlsx",
                    Map.of());
        }

        assertThat(TimelineNoise.find(events, classified, NAMES, List.of(), CAP)).isEmpty();
    }

    @Test
    void aPatternDecidedEitherWayIsntProposedAgainAndFewEventsAreNever() {
        heartbeat(120);
        assertThat(TimelineNoise.find(events, classified, NAMES, List.of(new Exclusion("Proxy", "Beacon-Ping", null,
                false, "kept by the user")), CAP)).isEmpty();

        events.clear();
        heartbeat(TimelineNoise.MIN_EVENTS - 1);
        assertThat(TimelineNoise.find(events, classified, NAMES, List.of(), CAP)).isEmpty();
    }

    @Test
    void aRateHighWithManyHostsAndAServiceAccountIsNoiseWithoutBeingPeriodic() {
        // Three hosts in the same minutes, at uneven times, the account a service's as its field says.
        for (int i = 0; i < 90; i++) {
            final long time = at("09:00") + (i / 3) * 37_000L + (i % 7) * 1_000L;
            add(time, 1, "Scan-File", "ws-0" + (i % 3), "C:\\temp\\f" + i, Map.of("Account/Type", "Service"));
        }
        browsing();

        final List<Noise> noise = TimelineNoise.find(events, classified, NAMES, List.of(), CAP);

        assertThat(noise).singleElement().satisfies(found -> assertThat(found.signals())
                .contains("on 3 or more hosts at once", "a service or system account, as its field says")
                .doesNotContain("at regular intervals"));
    }

    @Test
    void anExclusionAgreedToLeavesItsEventsOutAndIsKeptForTheNextRun() {
        heartbeat(120);
        browsing();
        final FakeTabWorkspace files = new FakeTabWorkspace(tempDir);
        Mockito.when(files.workspace().readLines(Mockito.anyInt(), Mockito.any(WorkspacePath.class),
                Mockito.anyLong(), Mockito.anyInt())).thenAnswer(invocation -> files.content(
                invocation.<WorkspacePath>getArgument(1).path()).orElse("").lines().toList());
        final SessionWorkspace workspace = new SessionWorkspace(files.workspace(), 1);
        final Exclusion agreed = TimelineNoise.find(events, classified, NAMES, List.of(), CAP).getFirst()
                .decided(true);

        TimelineNoise.keep(workspace, List.of(agreed, new Exclusion("Endpoint", "Copy", null, false, "kept")));
        final List<Exclusion> kept = TimelineNoise.kept(workspace);
        final TimelineNoise.Applied applied = TimelineNoise.apply(events, classified, NAMES, kept);

        assertThat(kept).containsExactly(agreed, new Exclusion("Endpoint", "Copy", null, false, "kept"));
        assertThat(applied.kept()).hasSize(events.size() - 120)
                .noneMatch(event -> classified.containsKey(event.id())
                                    && "Beacon-Ping".equals(classified.get(event.id()).typeId()));
        assertThat(applied.leftOut()).isEqualTo(120);
        assertThat(applied.left()).containsExactly(Map.entry(agreed, 120));
        // One the user kept leaves nothing out.
        assertThat(TimelineNoise.apply(events, classified, NAMES, List.of(new Exclusion("Proxy", "Beacon-Ping",
                null, false, ""))).leftOut()).isZero();
    }

    // A beacon every 30 seconds to one URL, with no title.
    private void heartbeat(final int count) {
        for (int i = 0; i < count; i++) {
            add(at("08:00") + i * 30_000L, 0, "Beacon-Ping", "ws-01", "https://telemetry.example.net/ping",
                    Map.of());
        }
    }

    // The user's own browsing in focus: varied pages, uneven times.
    private void browsing() {
        final long[] offsets = {0, 70, 130, 400, 410, 900, 1_300, 1_320, 2_000, 2_500};
        for (int i = 0; i < offsets.length; i++) {
            final String id = "f" + ids++;
            events.add(new Event(at("10:00") + offsets[i] * 1_000L, 1, id, Role.FOCUS, null, "ws-01", "chrome.exe",
                    "Page " + i, null, null, null, "7", String.valueOf(ids)));
            add(at("10:00") + offsets[i] * 1_000L + 500, 0, "View-Page", "ws-01", "https://news.example.org/" + i,
                    Map.of());
        }
    }

    private void add(final long time, final int source, final String type, final String host, final String object,
                     final Map<String, String> keys) {
        final String id = "e" + ids++;
        events.add(new Event(time, source, id, Role.ACTION, type, host, null, null, null, object, null, "7",
                String.valueOf(ids)));
        classified.put(id, new Classified(null, null, type, Role.ACTION, type, null, object, host, keys,
                Route.TYPE_FIELD));
    }

    private static long at(final String time) {
        return Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli();
    }
}
