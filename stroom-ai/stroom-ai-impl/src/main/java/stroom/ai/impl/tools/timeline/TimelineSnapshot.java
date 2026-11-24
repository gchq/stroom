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

import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What a timeline run was, written beside its tables (`timeline.json`) so its tables can be written elsewhere later
/// as they are, e.g. as a CSV Data Connection ([ProposeTimelineDashboardTool]): whose it is, the period, the user's
/// zone, the view and lanes asked for, the sources read, whether its activities were found, and when it was built.
///
/// @param entity        The user (or entity).
/// @param fromMs        The period's start.
/// @param toMs          The period's end.
/// @param zone          The user's zone, e.g. `UTC`.
/// @param view          The view asked for (activities, detail or both).
/// @param lanes         The lanes asked for, or null for the view's default.
/// @param sources       The sources read, by their names in the result.
/// @param characterised True if the activities were found, so `activity_label` is filled.
/// @param builtMs       When it was built.
/// @param laneTitles    What each table's lanes are, by table ([TimelineDataConnection#SPANS],
///                      [TimelineDataConnection#ACTIVITIES]), e.g. `Device host name (host.name)`
///                      ([TimelineLaneTitles]); empty for a snapshot from before they were written.
public record TimelineSnapshot(String entity,
                               long fromMs,
                               long toMs,
                               String zone,
                               String view,
                               String lanes,
                               List<String> sources,
                               boolean characterised,
                               long builtMs,
                               Map<String, String> laneTitles) {

    /// The file it is written to, in the timeline's folder.
    public static final String FILE = "timeline.json";

    /// Copies the sources and the titles.
    public TimelineSnapshot {
        sources = NullSafe.list(sources).stream().filter(NullSafe::isNonBlankString).toList();
        laneTitles = Map.copyOf(NullSafe.map(laneTitles));
    }

    /// The snapshot of a run.
    ///
    /// @param run     The run.
    /// @param builtMs When it was built.
    /// @return Its snapshot.
    static TimelineSnapshot of(final TimelineRun run, final long builtMs) {
        return new TimelineSnapshot(run.entity(), run.period().fromMs(), run.period().toMs(),
                run.zone().zone().getId(), run.view().argName(), run.lanes(), run.names(),
                run.characterised() != null, builtMs, Map.of(TimelineDataConnection.SPANS, run.spanLaneTitle(),
                        TimelineDataConnection.ACTIVITIES, run.activityLaneTitle()));
    }

    /// Writes it to the timeline's folder.
    ///
    /// @param workspace The chat's workspace.
    /// @param folder    The timeline's folder, e.g. `/outputs/timelines/jsmith/`.
    void write(final SessionWorkspace workspace, final String folder) {
        workspace.write(WorkspacePath.parse(folder + FILE), JsonUtil.writeValueAsString(this, true), null);
    }

    /// Reads the snapshot in a timeline's folder.
    ///
    /// @param workspace The chat's workspace.
    /// @param folder    The timeline's folder.
    /// @return The snapshot, or empty if there is none (no run, or one from before snapshots were written) or it
    /// can't be read.
    static Optional<TimelineSnapshot> read(final SessionWorkspace workspace, final String folder) {
        final WorkspacePath path = WorkspacePath.parse(folder + FILE);
        if (workspace.find(path).isEmpty()) {
            return Optional.empty();
        }
        try {
            final String json = Files.readString(workspace.localFile(path), StandardCharsets.UTF_8);
            return Optional.ofNullable(JsonUtil.readValue(json, TimelineSnapshot.class));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    /// @return When it was built, as an ISO 8601 instant.
    String built() {
        return Instant.ofEpochMilli(builtMs).toString();
    }
}
