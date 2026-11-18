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

import stroom.docref.DocRef;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The working copies a promotion writes back whose originals were changed in production since they were taken
/// (e.g. edited by hand), said for the promotion's approval. Decided by the user (2026-11-18): such an original is
/// always overwritten after its backup, never asked about on its own; the approval the user confirms says so.
final class ChangedOriginals {

    // When an original was last changed, as the approval says it.
    private static final DateTimeFormatter CHANGED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final BuildRecords buildRecords;
    private final DocumentWrites writes;

    /// @param buildRecords Holds the version each working copy was copied at.
    /// @param writes       Reads the original's fields.
    ChangedOriginals(final BuildRecords buildRecords, final DocumentWrites writes) {
        this.buildRecords = buildRecords;
        this.writes = writes;
    }

    /// @param build   The build.
    /// @param plan    The promotion's steps.
    /// @param backups The folder each original is backed up to before it is overwritten, e.g.
    ///                `System/MCP Workspace/backups`.
    /// @return For each write-back whose original changed in production since its working copy was taken: what
    /// changed it, by whom and when, and that it is overwritten after its backup; none when nothing changed (or it
    /// isn't known, for a copy taken before the copied version was recorded).
    List<String> said(final String build, final List<PromotionLayout.Step> plan, final String backups) {
        final List<String> out = new ArrayList<>();
        for (final PromotionLayout.Step step : plan) {
            if (PromotionLayout.WRITE_BACK.equals(step.action())) {
                final DocRef copy = new DocRef(String.valueOf(step.doc().get("type")),
                        String.valueOf(step.doc().get("uuid")), Objects.toString(step.doc().get("name"), null));
                changedSinceCopied(build, copy, step.target(), backups).ifPresent(out::add);
            }
        }
        return out;
    }

    // What the approval says of a working copy's original changed since the copy was taken; empty if it is as it
    // was, or the version it was copied at isn't known.
    private Optional<String> changedSinceCopied(final String build,
                                                final DocRef copy,
                                                final String originalUuid,
                                                final String backups) {
        final Object copiedVersion = buildRecords.entry(build, copy.getUuid()).get(BuildRecords.COPY_OF_VERSION);
        if (copiedVersion == null) {
            return Optional.empty();
        }
        final Map<String, Object> original = writes.fields(new DocRef(copy.getType(), originalUuid));
        if (copiedVersion.equals(original.get("version"))) {
            return Optional.empty();
        }
        final String by = original.get("updateUser") == null
                ? ""
                : " by " + original.get("updateUser");
        final String when = original.get("updateTimeMs") instanceof final Number ms
                ? " on " + CHANGED_AT.format(Instant.ofEpochMilli(ms.longValue()))
                : "";
        return Optional.of(copy.getType() + " '" + copy.getName() + "' was changed in production" + by + when
                           + " since it was copied: it is overwritten, its earlier version kept as a backup in "
                           + backups + " ('" + copy.getName() + " backup <date and time>')");
    }
}
