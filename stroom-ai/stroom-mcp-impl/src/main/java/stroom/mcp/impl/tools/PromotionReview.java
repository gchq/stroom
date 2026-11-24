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
import stroom.ai.impl.tools.sample.ToolJson;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/// What [PromoteBuildTool] shows the user before promoting: the layout to review in the chat, bound to its review id,
/// and the form of which streams a new version's filter reads.
class PromotionReview {

    // What a new version's filter reads, the recommended first.
    private static final List<String> BACKFILL_OPTIONS = List.of("From now on (recommended): streams arriving "
                                                                 + "after promotion",
            "Everything (backfill): existing streams too", "From a date: I'll give it");

    private final Consent consent;

    /// @param consent Asks the user which streams a new version's filter reads.
    PromotionReview(final Consent consent) {
        this.consent = consent;
    }

    /// What a new version's filter reads, as the user chooses at its promotion: from now on (the default, and
    /// without a form), everything (a backfill: enabled, it reprocesses the whole feed, so both versions' Events
    /// exist for old data), or from a date they give.
    ///
    /// @param call    The call.
    /// @param context The call's context.
    /// @param build   The build promoted.
    /// @return What the new version's filter reads.
    Backfill backfill(final ToolCall call, final ToolCallContext context, final String build) {
        final String question = "Build '" + build + "' promotes a new version of a promoted pipeline. Enabled, a "
                                + "filter reading everything reprocesses the whole feed, so both versions' Events "
                                + "exist for its old data. Which streams does the new version's filter read?";
        final Optional<String> chosen = consent.choose(call, context, PromoteBuildTool.NAME, question,
                BACKFILL_OPTIONS, "Which streams the new version's filter reads");
        if (chosen.isEmpty() || BACKFILL_OPTIONS.getFirst().equals(chosen.get())) {
            return new Backfill(false, null, "streams arriving from promotion on" + (chosen.isEmpty()
                    ? " (no form could be shown: the default)"
                    : " (as the user chose)"));
        }
        if (BACKFILL_OPTIONS.get(1).equals(chosen.get())) {
            return new Backfill(true, null, "every stream of the feed, existing ones too (a backfill, as the user "
                                            + "chose)");
        }
        final String given = consent.answer(call, context, PromoteBuildTool.NAME, "From which date does the new "
                                                                                  + "version's filter read streams "
                                                                                  + "(UTC, yyyy-MM-dd)?", "From date",
                        LocalDate.now(ZoneOffset.UTC).toString())
                .orElseThrow(() -> new ToolRefusal("Not promoted: ask the user from which date (yyyy-MM-dd) the new "
                                                   + "version's filter reads, then call again"));
        final LocalDate date;
        try {
            date = LocalDate.parse(given.strip());
        } catch (final DateTimeParseException e) {
            throw new ToolRefusal("Not promoted: '" + given + "' is not a date (yyyy-MM-dd). Nothing was done.");
        }
        return new Backfill(false, date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                "streams created from " + date + " (UTC) on, as the user chose");
    }

    /// The digest of everything a review shows but its hint (which says what to do next).
    ///
    /// @param review The review shown.
    /// @return Its digest.
    static String shownDigest(final Map<String, Object> review) {
        final Map<String, Object> shown = new TreeMap<>(review);
        shown.remove("hint");
        return Consent.reviewId(ToolJson.dumpsSortedKeys(shown));
    }

    /// What the latest layout shown for a build is remembered under, in the chat's workspace.
    ///
    /// @param build The build.
    /// @return What it is remembered under.
    static String shownTarget(final String build) {
        return "promotion:" + build;
    }

    /// The layout for the user to review, shown in the chat first: a list of moves crammed into a form was hard to
    /// read, and where things would go wasn't plain (asked for by the user). Passed on only with this layout's id
    /// (seen: reviewed=true on the first call, and the user approved a form whose layout was never shown).
    ///
    /// @param build     The build.
    /// @param tree      The layout, as a tree of folders.
    /// @param bound     What the approval is bound to: its review id is this digest's.
    /// @param warnings  What the layout warns of.
    /// @param processed What each promoted pipeline goes on processing.
    /// @param roots     Where each role goes.
    /// @param rolesHint What changes where a role's documents go, or null.
    /// @param reviewed  The review id the call gave, if any.
    /// @return The review result.
    static Map<String, Object> review(final String build,
                                      final String tree,
                                      final String bound,
                                      final List<String> warnings,
                                      final List<String> processed,
                                      final List<PromotionRoleRoots.RoleRoot> roots,
                                      final String rolesHint,
                                      final Object reviewed) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "needs_review");
        result.put("build", build);
        result.put("layout", tree);
        result.put("review_id", Consent.reviewId(bound));
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }
        if (!processed.isEmpty()) {
            result.put("processing_after_promotion", processed);
        }
        final List<Map<String, Object>> locations = new ArrayList<>();
        for (final PromotionRoleRoots.RoleRoot root : roots) {
            final Map<String, Object> location = new LinkedHashMap<>();
            location.put("role", root.role());
            location.put("folder", root.folder());
            location.put("why", root.why());
            location.put("options", root.options());
            locations.add(location);
        }
        if (!locations.isEmpty()) {
            result.put("role_locations", locations);
        }
        final boolean asked = roots.stream().allMatch(root -> !root.toAsk());
        result.put("hint", "Show the user the layout in the chat exactly as it is (a code block), with any warnings "
                           + "and the processing after promotion, and ask them to confirm it or say what to change "
                           + "(a folder: destinations by type or document uuid; a role's location: destinations "
                           + "keyed by the role, e.g. {'Indexing': '/Elastic Indices'})"
                           + (asked
                ? ""
                : "; and where each role's documents go (role_locations: the first option is proposed, the "
                  + "others are the feed's own folder or a folder the user names): their answer goes in "
                  + "destinations keyed by the role, then show the tree again")
                           + (rolesHint == null
                ? ""
                : ". " + rolesHint.substring(0, rolesHint.length() - 1))
                           + ". Once they confirm it, promote_build again with the same destinations and reviewed='"
                           + Consent.reviewId(bound) + "' (this layout's review_id): they approve it "
                           + "in a short form. If they change something, call again with their changes and no "
                           + "reviewed: the new layout is shown the same way." + Consent.reviewNote(reviewed));
        return result;
    }


    // --------------------------------------------------------------------------------


    /// What a new version's filter reads: everything, else streams created from fromMs on (null: from the moment it
    /// is made); said as the result says it.
    ///
    /// @param everything True if it reads every stream of the feed.
    /// @param fromMs     The time it reads streams created from, or null.
    /// @param said       What it reads, as the result says it.
    record Backfill(boolean everything, Long fromMs, String said) {

    }
}
