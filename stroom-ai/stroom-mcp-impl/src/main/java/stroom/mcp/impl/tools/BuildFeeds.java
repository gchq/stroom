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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.feed.shared.FeedDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The feeds of builds: a feed made in a build, never one of the same name elsewhere or one differing only in case;
/// samples sent only to a build's feeds; and the test feed of a build on existing feeds.
class BuildFeeds {

    /// The build record's key for the sample files the user chose instead of an existing feed's data.
    static final String USER_SAMPLES = "user_samples";
    /// The build record's key saying a feed's samples are files on the user's disk (commands were given for them).
    static final String SAMPLE_FILES = "sample_files";
    /// The build record's key naming the existing feeds a test feed holds the Events of.
    static final String OUTPUT_FOR = "output_for";

    private final FeedNames feedNames;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final BuildPlan plan;
    private final DocumentAccess documentAccess;

    /// @param feedNames      Finds feeds by name, in any case.
    /// @param guard          Makes the build's feeds, and checks a feed is the tools' own.
    /// @param records        Keeps what is known of each feed in the build's record.
    /// @param plan           Says which existing feeds a build works from.
    /// @param documentAccess Reads and writes feeds as the user.
    @Inject
    BuildFeeds(final FeedNames feedNames,
               final WriteGuard guard,
               final BuildRecords records,
               final BuildPlan plan,
               final DocumentAccess documentAccess) {
        this.feedNames = feedNames;
        this.guard = guard;
        this.records = records;
        this.plan = plan;
        this.documentAccess = documentAccess;
    }

    /// The feed of exactly this name, when this build already has it (an agent's retry: it carries on with it); a
    /// feed of the name anywhere else is refused, before the user is asked or anything is made. Seen on the local
    /// stack: Stroom refused the duplicate only after the user had confirmed it, as a bare 500, leaving the new
    /// build's folder empty.
    ///
    /// @param name  The feed's name.
    /// @param build The build.
    /// @return The build's feed of the name, as create_feed gives it (`existing: true`); empty when there is no
    /// feed of the name.
    /// @throws ToolRefusal if a feed of the name is outside the build.
    Optional<Map<String, Object>> existingFeed(final String name, final String build) {
        // Exact, and not lagging.
        final Optional<DocRef> ref = feedNames.exact(name);
        if (ref.isEmpty()) {
            return Optional.empty();
        }
        if (guard.buildOf(ref.get()).filter(build::equals).isPresent()) {
            final FeedDoc doc = read(ref.get());
            final Map<String, Object> existing = new LinkedHashMap<>();
            existing.put("type", FeedDoc.TYPE);
            existing.put("uuid", doc.getUuid());
            existing.put("name", doc.getName());
            existing.put("stream_type", doc.getStreamType());
            existing.put("encoding", doc.getEncoding());
            existing.put("existing", true);
            existing.put("note", "This build already has feed '" + doc.getName() + "': nothing new was made; carry "
                                 + "on with it.");
            return Optional.of(existing);
        }
        final String where = NullSafe.nonBlankStringElse(path(ref.get()), "outside the workspace");
        throw new ToolRefusal("A feed named '" + name + "' already exists (" + where + "), and Stroom keeps one "
                              + "feed per name. If it is the same source, use that feed: upload to it, or build "
                              + "from the data it holds (start_build feeds=['" + name + "'], then survey_feed with "
                              + "the build); if not, propose another name, such as the next version.");
    }

    /// Seen (Qwen, VS Code): DELINEA-SECRETSERVER-V1.0 made beside an earlier Delinea-SecretServer-V1.0; its sample
    /// stream was filed under the earlier spelling, processing refused it as another build's feed, and the agent
    /// spent the rest of the session looking for a second feed.
    ///
    /// @param name A feed's proposed name.
    /// @throws ToolRefusal if a feed differs from it only in case.
    void refuseCaseTwin(final String name) {
        final List<DocRef> twins = feedNames.feedsNamed(name).stream()
                .filter(feed -> !name.equals(feed.getName()))
                .toList();
        if (!twins.isEmpty()) {
            final String said = String.join(", ", twins.stream()
                    .map(feed -> "'" + feed.getName() + "' (" + NullSafe.nonBlankStringElse(path(feed),
                            "path unknown") + ")")
                    .toList());
            throw new ToolRefusal("A feed named " + said + " already exists, differing from '" + name + "' only in "
                                  + "case. Stroom files a feed's streams under its name without regard to case, so "
                                  + "the two would share their streams. Choose a name that differs in more than "
                                  + "case (a version, say), or, if it is the same source, use the existing feed, or "
                                  + "have it deleted first.");
        }
    }

    /// The feed's doc ref, refused unless it is in a build: samples never go to a production feed. In a build on an
    /// existing feed's data, only to a test feed for sample files the user chose (create_feed user_samples=).
    ///
    /// @param session The session the call is made in, for the audit log.
    /// @param feed    The feed's name.
    /// @return The feed.
    /// @throws ToolRefusal if there is no such feed, the tools didn't make it, or it isn't one for samples.
    DocRef buildFeed(final ToolSession session, final String feed) {
        // A direct lookup.
        final DocRef match = feedNames.exact(feed)
                .orElseThrow(() -> FeedNames.noFeedNamed(feed));
        guard.checkManaged(session, match);
        final String build = guard.buildOf(match).orElse(null);
        final List<String> sources = plan.worksFrom(build);
        if (!sources.isEmpty() && !JsonValues.truthy(records.entry(build, match.getUuid()).get(USER_SAMPLES))) {
            throw new ToolRefusal(worksFromMessage(build, sources) + " Feed " + feed + " wasn't made for sample "
                                  + "files the user chose instead: upload nothing to it.");
        }
        return match;
    }

    /// @param feed A build's feed.
    /// @return Whether the feed's samples were given commands: its samples are files, never text (upload_sample
    /// refuses it). Kept in the build's record.
    boolean sampleFiles(final DocRef feed) {
        return guard.buildOf(feed)
                .map(build -> JsonValues.truthy(records.entry(build, feed.getUuid()).get(SAMPLE_FILES)))
                .orElse(false);
    }

    /// @param build   A build on existing feeds.
    /// @param sources The feeds it works from.
    /// @return What such a build makes and uploads (nothing but its test feed).
    static String worksFromMessage(final String build, final List<String> sources) {
        final String feeds = String.join(", ", sources);
        return "Build '" + build + "' works from the existing feed(s) " + feeds + ": it is built on their data where "
               + "it is, stepped (survey_feed, step_sample with their stream ids, step_records over the survey's "
               + "locations) and processed (create_processor_filter with their stream ids: the server writes the "
               + "Events to a test feed it makes in the build, or to one the user names: create_feed "
               + "test_feed=true). It creates no other feed and uploads nothing, and sample files in the user's "
               + "workspace aren't used.";
    }

    /// The build's test feed for the existing feeds it works from: their settings, holding only the Events its
    /// pipelines make from their streams, recorded as their output (the processing tools then write there). For
    /// create_feed `test_feed=true`, and for the processing tools, which make `<FEED>-MCP-TEST` when the user named
    /// none.
    ///
    /// @param build   The build.
    /// @param name    The test feed's name.
    /// @param sources The existing feeds the build works from.
    /// @return The test feed, written.
    FeedDoc makeTestFeed(final String build, final String name, final List<String> sources) {
        final FeedDoc settings = feedNames.exact(sources.getFirst())
                .flatMap(source -> documentAccess.read(source, FeedDoc.class))
                .orElse(null);
        final DocRef ref = guard.create(FeedDoc.TYPE, name, build, null, false, null);
        final FeedDoc found = write(read(ref).copy()
                .encoding(NullSafe.nonBlankStringElse(NullSafe.get(settings, FeedDoc::getEncoding), "UTF-8"))
                .streamType(NullSafe.nonBlankStringElse(NullSafe.get(settings, FeedDoc::getStreamType),
                        "Raw Events"))
                .description("Build '" + build + "': the Events its pipelines make from " + String.join(", ", sources)
                             + "'s streams. Nothing is uploaded to it; it stays in the workspace at promotion.")
                .build());
        records.update(build, new DocRef(FeedDoc.TYPE, found.getUuid(), found.getName()),
                entry -> entry.put(OUTPUT_FOR, List.copyOf(sources)));
        return found;
    }

    /// @param ref A feed.
    /// @return It, as the user reads it.
    /// @throws ToolRefusal if it can't be read.
    FeedDoc read(final DocRef ref) {
        return documentAccess.read(ref, FeedDoc.class)
                .orElseThrow(() -> new ToolRefusal("The feed " + ref.getUuid() + " can't be read"));
    }

    /// @param doc A feed, changed.
    /// @return It, saved as the user.
    @SuppressWarnings("unchecked")
    FeedDoc write(final FeedDoc doc) {
        final DocumentActionHandler<FeedDoc> handler = documentAccess.handler(FeedDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Feed store"));
        return handler.writeDocument(doc);
    }

    private String path(final DocRef ref) {
        try {
            return documentAccess.path(ref);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
