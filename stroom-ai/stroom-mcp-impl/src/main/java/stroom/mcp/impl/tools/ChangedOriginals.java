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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.generation.CodeDiff;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The working copies a promotion writes back whose originals were changed in production since they were taken
/// (e.g. edited by hand): the user chooses whether each copy overwrites its original, told how the two differ; kept,
/// the copy stays in the build (asked for by the user: a production edit made after the copy was taken was lost, but
/// for its backup). The choice is kept in the build's record against the original's version, so the review and the
/// approval ask once; changed again, it is asked again. Where the form can't be shown, the user's answer in the chat
/// is honoured on the next call ([#ARG]; decided by the user, 2026-11-18: never a dead end).
final class ChangedOriginals {

    /// The user's answer, given in the chat, for each working copy whose original was changed in production: by the
    /// working copy's UUID, `overwrite` or `keep` (where the form asking couldn't be shown).
    static final String ARG = "changed_originals";
    /// The choice that writes the working copy back over production.
    static final String OVERWRITE_ORIGINAL = "Overwrite production with the working copy";
    /// The choice that leaves production as it is, the working copy in the build.
    static final String KEEP_ORIGINAL = "Keep production as it is";
    /// In a working copy's record entry: what the user chose for an original changed since, and at which version.
    static final String ORIGINAL_CHOICE = "original_choice";

    private static final String KEEP = "keep";
    private static final String OVERWRITE = "overwrite";
    // The most of a diff a question quotes: the form's message must stay short enough to show.
    private static final int MAX_DIFF_CHARS = 1_000;
    // The most lines of each side of the difference a question quotes.
    private static final int MAX_DIFF_LINES = 6;

    private final BuildRecords buildRecords;
    private final Consent consent;
    private final DocumentWrites writes;

    /// @param buildRecords Holds each working copy's copied version and the user's choice.
    /// @param consent      Asks the user in a form.
    /// @param writes       Reads the copy's and its original's fields.
    ChangedOriginals(final BuildRecords buildRecords, final Consent consent, final DocumentWrites writes) {
        this.buildRecords = buildRecords;
        this.consent = consent;
        this.writes = writes;
    }

    /// Asks about each write-back whose original changed since the copy was taken, and makes a kept one a keep step.
    ///
    /// @param call    The promotion's call.
    /// @param context Its context.
    /// @param action  The tool asking, e.g. `promote_build`.
    /// @param build   The build.
    /// @param plan    The promotion's steps: a write-back the user keeps production for becomes a keep, in place.
    /// @param answers The user's answers given in the chat ([#ARG]), by working copy UUID.
    /// @return True if any working copy was kept.
    /// @throws ToolRefusal if an answer is neither `overwrite` nor `keep`, or a question couldn't be shown as a form
    ///                     (then saying what to ask in the chat and how to give the answer).
    boolean ask(final ToolCall call,
                final ToolCallContext context,
                final String action,
                final String build,
                final List<PromotionLayout.Step> plan,
                final Map<String, String> answers) {
        boolean anyKept = false;
        final List<String> unasked = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            final PromotionLayout.Step step = plan.get(i);
            if (!PromotionLayout.WRITE_BACK.equals(step.action())) {
                continue;
            }
            final DocRef copy = ref(step.doc());
            final Optional<ChangedOriginal> changed = changedSinceCopied(build, copy, step.target());
            if (changed.isEmpty()) {
                continue;
            }
            final Map<String, Object> entry = buildRecords.entry(build, copy.getUuid());
            Boolean keep = keepAnswer(answers.get(copy.getUuid()), copy);
            final boolean recorded = keep == null && entry.get(ORIGINAL_CHOICE) instanceof final Map<?, ?> choice
                                     && changed.get().version().equals(choice.get("version"));
            if (recorded) {
                keep = Boolean.TRUE.equals(((Map<?, ?>) entry.get(ORIGINAL_CHOICE)).get("keep"));
            } else if (keep == null) {
                final Optional<String> chosen = consent.choose(call, context, action, changed.get().said(),
                        List.of(OVERWRITE_ORIGINAL, KEEP_ORIGINAL), copy.getType() + " '" + copy.getName()
                                                                    + "' was changed in production since it was "
                                                                    + "copied");
                if (chosen.isEmpty()) {
                    unasked.add(changed.get().said() + " (working copy " + copy.getUuid() + ")");
                    continue;
                }
                keep = KEEP_ORIGINAL.equals(chosen.get());
            }
            if (!recorded) {
                final boolean kept = keep;
                buildRecords.update(build, copy, record -> record.put(ORIGINAL_CHOICE, Map.of("version",
                        changed.get().version(), "keep", kept)));
            }
            if (keep) {
                plan.set(i, new PromotionLayout.Step(step.doc(), PromotionLayout.KEEP, step.target(), step.role()));
                anyKept = true;
            }
        }
        if (!unasked.isEmpty()) {
            throw new ToolRefusal("Not promoted yet: the question couldn't be shown as a form. " + String.join(" ",
                    unasked) + " Ask the user in the chat, then " + action + " again with the same destinations and "
                                  + ARG + " giving their answer for each working copy by its uuid, "
                                  + "'" + OVERWRITE + "' or '" + KEEP + "', e.g. {'" + unaskedExample(plan, answers)
                                  + "': '" + KEEP + "'}. Nothing was changed.");
        }
        return anyKept;
    }

    /// @param build        The build.
    /// @param copy         A working copy.
    /// @param originalUuid Its original's UUID.
    /// @return What changed in the original since the copy was taken, said for the user with the choice, and its
    /// version now; empty if it is as it was (or it isn't known, for a copy taken before this was recorded).
    Optional<ChangedOriginal> changedSinceCopied(final String build, final DocRef copy, final String originalUuid) {
        final Object copiedVersion = buildRecords.entry(build, copy.getUuid()).get(BuildRecords.COPY_OF_VERSION);
        if (copiedVersion == null) {
            return Optional.empty();
        }
        final Map<String, Object> original = writes.fields(new DocRef(copy.getType(), originalUuid));
        if (copiedVersion.equals(original.get("version"))) {
            return Optional.empty();
        }
        final Map<String, Object> copied = writes.fields(copy);
        final String by = original.get("updateUser") == null
                ? ""
                : " by " + original.get("updateUser");
        final String when = original.get("updateTimeMs") instanceof final Number ms
                ? " at " + Instant.ofEpochMilli(ms.longValue())
                : "";
        final String difference = original.get("data") instanceof final String productionCode
                                  && copied.get("data") instanceof final String copyCode
                ? difference(productionCode, copyCode)
                : "";
        return Optional.of(new ChangedOriginal(original.get("version"), copy.getType() + " '" + copy.getName()
                           + "' was changed in production since its working "
                           + "copy was taken" + by + when + "." + difference + " " + OVERWRITE_ORIGINAL + " (the "
                           + "production change is then kept only in its backup), or "
                           + KEEP_ORIGINAL.toLowerCase(Locale.ROOT) + " (the working copy stays in the build, for "
                           + "its change to be made again on a fresh working copy)?"));
    }

    // The user's answer for a working copy given in the chat: true to keep production, false to overwrite it, null
    // when none was given.
    private static Boolean keepAnswer(final String answer, final DocRef copy) {
        if (NullSafe.isBlankString(answer)) {
            return null;
        }
        final String said = answer.strip().toLowerCase(Locale.ROOT);
        if (said.equals(KEEP) || said.equals(KEEP_ORIGINAL.toLowerCase(Locale.ROOT))) {
            return true;
        } else if (said.equals(OVERWRITE) || said.equals(OVERWRITE_ORIGINAL.toLowerCase(Locale.ROOT))) {
            return false;
        }
        throw new ToolRefusal(ARG + " for " + copy.getType() + " '" + copy.getName() + "' (" + copy.getUuid()
                              + ") is '" + answer + "': give '" + OVERWRITE + "' or '" + KEEP + "'. Nothing was "
                              + "changed.");
    }

    // A working copy's uuid for the example: the first written back with no answer given.
    private static String unaskedExample(final List<PromotionLayout.Step> plan, final Map<String, String> answers) {
        return plan.stream()
                .filter(step -> PromotionLayout.WRITE_BACK.equals(step.action()))
                .map(step -> String.valueOf(step.doc().get("uuid")))
                .filter(uuid -> !answers.containsKey(uuid))
                .findFirst()
                .orElse("<working copy uuid>");
    }

    // How production and the copy differ, what overwriting would lose from production first (found testing it: in
    // the code's order, the copy's change filled the lines shown and production's own edit was cut off).
    private static String difference(final String productionCode, final String copyCode) {
        final List<String> lost = new ArrayList<>();
        final List<String> added = new ArrayList<>();
        for (final String line : CodeDiff.diff(productionCode, copyCode, Integer.MAX_VALUE)) {
            if (line.startsWith("- ")) {
                lost.add(line.substring(2));
            } else if (line.startsWith("+ ")) {
                added.add(line.substring(2));
            }
        }
        return PipelineTemplates.shorten((lost.isEmpty()
                ? ""
                : " Overwriting it loses from production: " + lines(lost) + ".")
                + (added.isEmpty()
                ? ""
                : " The working copy brings: " + lines(added) + "."), MAX_DIFF_CHARS);
    }

    private static String lines(final List<String> lines) {
        final List<String> shown = lines.subList(0, Math.min(MAX_DIFF_LINES, lines.size()));
        return String.join(" | ", shown) + (lines.size() > shown.size()
                ? " | ... and " + (lines.size() - shown.size()) + " more lines"
                : "");
    }

    private static DocRef ref(final Map<String, Object> doc) {
        return new DocRef(String.valueOf(doc.get("type")), String.valueOf(doc.get("uuid")),
                Objects.toString(doc.get("name"), null));
    }


    // --------------------------------------------------------------------------------


    /// An original changed since its working copy was taken.
    ///
    /// @param version Its version now.
    /// @param said    What changed, for the user, with the choice.
    record ChangedOriginal(Object version, String said) {

    }
}
