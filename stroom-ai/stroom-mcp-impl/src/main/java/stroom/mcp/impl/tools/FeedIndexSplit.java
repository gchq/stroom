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

import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.index.shared.LuceneIndexDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/// One index for a build's several feeds, or one per feed (decided by the user, 2026-11-25: check permissions,
/// then ask). Before the build makes its index, who may read each feed's data is compared ([FeedReadAccess]):
/// where it differs (or can't be told), one index per feed is proposed, and merging them needs the user's explicit
/// choice, told that a shared index gives everyone who can use it every feed's events; where it matches, the user
/// is asked. The choice is kept in the build ([BuildRecords#FEED_COMPARISON]), and each index made after a choice of
/// one per feed is named for its feed.
class FeedIndexSplit {

    /// The create_index_doc argument giving the user's answer: [BuildRecords#INDEX_PER_FEED] or
    /// [BuildRecords#INDEX_SHARED].
    static final String INDEX_SPLIT_ARG = "index_split";
    /// The question put.
    static final String QUESTION = "One index for these feeds' events, or one per feed?";
    /// The answer for one index per feed.
    static final String PER_FEED = "One index per feed";
    /// The answer for one shared index, with what it means.
    static final String SHARED = "One shared index: everyone who can use it sees every feed's events";
    private static final String CHOICE = "index";

    private final BuildPlan plan;
    private final BuildRecords records;
    private final FeedReadAccess readAccess;
    private final Consent consent;
    private final WriteGuard guard;

    /// @param plan       Gives the feeds a build is for.
    /// @param records    Keeps the user's choice in the build.
    /// @param readAccess Says who may read each feed.
    /// @param consent    Asks the user in a form.
    /// @param guard      Lists the build's index docs.
    @Inject
    FeedIndexSplit(final BuildPlan plan,
                   final BuildRecords records,
                   final FeedReadAccess readAccess,
                   final Consent consent,
                   final WriteGuard guard) {
        this.plan = plan;
        this.records = records;
        this.readAccess = readAccess;
        this.consent = consent;
        this.guard = guard;
    }

    /// Settles, for a build of several feeds, whether the index about to be made is shared by them or one feed's.
    ///
    /// @param call      The create_index_doc call (its `index_split`, given on the user's word).
    /// @param context   The call's context.
    /// @param build     The build.
    /// @param docName   The index doc's name.
    /// @param indexName The Elasticsearch index's name, or null.
    /// @return What to tell the agent of it (`index_split`, `for_feed` where one per feed, `readers`), or empty for
    /// a build of one feed.
    /// @throws ToolRefusal when the user hasn't chosen yet and no form can be shown, or an index of one per feed
    /// names no feed.
    Optional<Map<String, Object>> settle(final ToolCall call,
                                         final ToolCallContext context,
                                         final String build,
                                         final String docName,
                                         final String indexName) {
        final List<String> feeds = plan.buildFeeds(build);
        if (feeds.size() < 2) {
            return Optional.empty();
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        String choice = NullSafe.string(String.valueOf(records.feedComparison(build).getOrDefault(CHOICE, "")));
        final Object given = ToolArgs.raw(call, INDEX_SPLIT_ARG);
        // Asked again where index_split names another choice than the one kept: the user changed their mind (seen
        // in eval case 105, 2026-11-25: every later call kept the first choice).
        if (choice.isEmpty() || (given != null && !String.valueOf(given).strip().equals(choice))) {
            final FeedReadAccess.Access access = readAccess.access(feeds);
            choice = chosen(call, context, build, feeds, access);
            records.rememberFeedComparison(build, Map.of(CHOICE, choice, "index_readers", access.said()));
            out.put("readers", access.said());
        }
        out.put(INDEX_SPLIT_ARG, choice);
        if (BuildRecords.INDEX_SHARED.equals(choice)) {
            out.put("said", "One index for every feed's events, as the user chose: everyone who can use it sees "
                            + "them all.");
            return Optional.of(out);
        }
        final String text = (NullSafe.string(docName) + " " + NullSafe.string(indexName)).toLowerCase(Locale.ROOT);
        final List<String> named = feedsNamed(feeds, text);
        final List<String> without = feedsWithoutIndex(build, feeds);
        if (named.size() != 1) {
            throw new ToolRefusal("The user chose one index per feed for build '" + build + "': name this index doc "
                                  + (indexName == null
                    ? ""
                    : "and its Elasticsearch index ") + "for exactly one feed (e.g. '" + without.stream()
                                          .findFirst()
                                          .orElse(feeds.getFirst()) + "-...'), and make one for each of "
                                  + String.join(", ", without) + ". Nothing was made.");
        }
        out.put("for_feed", named.getFirst());
        final List<String> still = without.stream()
                .filter(feed -> !feed.equalsIgnoreCase(named.getFirst()))
                .toList();
        out.put("said", "One index per feed, as the user chose: this one holds " + named.getFirst() + "'s events"
                        + (still.isEmpty()
                ? "."
                : "; still to make, one each: " + String.join(", ", still) + "."));
        return Optional.of(out);
    }

    /// @param feeds The feeds a build is for.
    /// @param text  An index's name (its doc's, and its Elasticsearch index's), any case.
    /// @return The feeds it names; a feed's name inside another's (ACME in ACME-B) is that other's.
    static List<String> feedsNamed(final List<String> feeds, final String text) {
        final String lower = NullSafe.string(text).toLowerCase(Locale.ROOT);
        final List<String> matched = feeds.stream()
                .filter(feed -> lower.contains(feed.toLowerCase(Locale.ROOT)))
                .toList();
        return matched.stream()
                .filter(feed -> matched.stream().noneMatch(other -> other.length() > feed.length()
                                                                    && other.toLowerCase(Locale.ROOT)
                                                                            .contains(feed.toLowerCase(Locale.ROOT))))
                .toList();
    }

    // The user's choice: given as index_split= (on their word), else asked in a form.
    private String chosen(final ToolCall call,
                          final ToolCallContext context,
                          final String build,
                          final List<String> feeds,
                          final FeedReadAccess.Access access) {
        final Object given = ToolArgs.raw(call, INDEX_SPLIT_ARG);
        // Only where no form can be shown: the form is the user's, never the agent's to answer (seen in eval case
        // 105, 2026-11-25: index_split=shared passed with forms available, and the user was never asked).
        if (given != null && !context.userQuestions().canAsk()) {
            final String answer = String.valueOf(given).strip();
            if (!BuildRecords.INDEX_PER_FEED.equals(answer) && !BuildRecords.INDEX_SHARED.equals(answer)) {
                throw new ToolRefusal(INDEX_SPLIT_ARG + " is '" + BuildRecords.INDEX_PER_FEED + "' or '"
                                      + BuildRecords.INDEX_SHARED + "', as the user chose, not '" + answer + "'");
            }
            return answer;
        }
        final String why = access.same()
                ? "The feeds " + String.join(", ", feeds) + " have the same readers (" + access.said() + "). "
                : "Who may read these feeds differs, or can't be told (" + access.said() + "): one index per feed is "
                  + "recommended. A shared index gives everyone who can use it every feed's events. ";
        final List<String> options = access.same()
                ? List.of(SHARED, PER_FEED)
                : List.of(PER_FEED + " (recommended)", SHARED);
        final Optional<String> answer = consent.choose(call, context, CreateIndexDocTool.NAME, why + QUESTION,
                options, QUESTION);
        if (answer.isEmpty()) {
            throw new ToolRefusal("Not made yet: build '" + build + "' is for several feeds. " + why + "Ask the "
                                  + "user: " + QUESTION + " Then call again with " + INDEX_SPLIT_ARG + "="
                                  + BuildRecords.INDEX_PER_FEED + " or " + INDEX_SPLIT_ARG + "="
                                  + BuildRecords.INDEX_SHARED + (access.same()
                    ? ""
                    : " (shared only on their explicit word, having heard that it gives everyone who can use it "
                      + "every feed's events)") + ". Nothing was made.");
        }
        return answer.get().startsWith(PER_FEED)
                ? BuildRecords.INDEX_PER_FEED
                : BuildRecords.INDEX_SHARED;
    }

    // The feeds no index doc of the build is named for yet.
    private List<String> feedsWithoutIndex(final String build, final List<String> feeds) {
        final List<String> names = new ArrayList<>();
        guard.folderContents(build).stream()
                .filter(doc -> LuceneIndexDoc.TYPE.equals(doc.type()) || ElasticIndexDoc.TYPE.equals(doc.type()))
                .forEach(doc -> names.add(doc.name().toLowerCase(Locale.ROOT)));
        return feeds.stream()
                .filter(feed -> names.stream().noneMatch(name -> name.contains(feed.toLowerCase(Locale.ROOT))))
                .toList();
    }
}
