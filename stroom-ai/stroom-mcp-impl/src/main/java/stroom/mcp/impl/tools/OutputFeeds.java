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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The build's test feed, where a pipeline processing the existing feeds a build works from writes its Events
/// (asked for by the user): set on the pipeline's StreamAppender (its feed property) in the build, and taken off
/// again at promotion. Ported from the Stroom MCP server's `tools/processing_writes.py` (`_feed_taken`,
/// `_free_feed_name`, `output_feed` and `restore_output_feed`; its `make_test_feed` is [BuildFeeds#makeTestFeed]).
class OutputFeeds {

    /// What the tools call a build's test feed: the source feed's name with this after it.
    static final String TEST_FEED = "-MCP-TEST";
    /// The last of the numbered names tried for a test feed.
    static final int LAST_FREE_NAME = 99;

    private static final String STREAM_APPENDER = "StreamAppender";
    private static final String FEED = "feed";

    private final PipelineShapes pipelineShapes;
    private final PipelineDocs pipelineDocs;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final BuildFeeds buildFeeds;
    private final FeedNames feedNames;

    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    /// @param pipelineDocs   Reads and saves pipelines as the user.
    /// @param guard          Lists the build's documents.
    /// @param records        Says which of the build's feeds are test feeds.
    /// @param buildFeeds     Makes the test feed.
    /// @param feedNames      Finds feeds by name, in any case.
    @Inject
    OutputFeeds(final PipelineShapes pipelineShapes,
                final PipelineDocs pipelineDocs,
                final WriteGuard guard,
                final BuildRecords records,
                final BuildFeeds buildFeeds,
                final FeedNames feedNames) {
        this.pipelineShapes = pipelineShapes;
        this.pipelineDocs = pipelineDocs;
        this.guard = guard;
        this.records = records;
        this.buildFeeds = buildFeeds;
        this.feedNames = feedNames;
    }

    /// @param name A feed's name.
    /// @return True if Stroom has a feed of this name, whatever its case (Stroom keeps one feed per name).
    boolean feedTaken(final String name) {
        // Exact, and not lagging.
        return feedNames.exact(name).isPresent() || !feedNames.feedsNamed(name).isEmpty();
    }

    /// @param name A feed's name.
    /// @return The name, or the first of `name-2`, `name-3`, ... no feed has.
    /// @throws ToolRefusal if they are all taken.
    String freeFeedName(final String name) {
        if (!feedTaken(name)) {
            return name;
        }
        for (int n = 2; n <= LAST_FREE_NAME; n++) {
            final String candidate = name + "-" + n;
            if (!feedTaken(candidate)) {
                return candidate;
            }
        }
        throw new ToolRefusal("Feeds " + name + " to " + name + "-" + LAST_FREE_NAME + " all exist: name the build's "
                              + "test feed (create_feed test_feed=true)");
    }

    /// The build's test feed for a pipeline processing the existing feeds it works from (asked for by the user):
    /// the pipeline's StreamAppender writes there (its feed property) instead of into the production feed. The one
    /// the user named (create_feed test_feed=true), or else made here as `<FEED>-MCP-TEST` (the next free name if
    /// another build has it), with the source feed's settings, holding only output: nothing is uploaded to it.
    /// Promotion leaves it in the workspace and takes the property off ([#restoreOutputFeed]).
    ///
    /// @param pipeline The pipeline.
    /// @param sources  The existing feeds the build works from.
    /// @param build    The build.
    /// @return The test feed; empty for a pipeline with no StreamAppender (nothing written to a feed).
    Optional<OutputFeed> outputFeed(final DocRef pipeline, final List<String> sources, final String build) {
        final List<String> appenders = appenders(pipeline.getUuid());
        if (appenders.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, WriteGuard.BuildDoc> feeds = new LinkedHashMap<>();
        guard.folderContents(build).stream()
                .filter(doc -> FeedDoc.TYPE.equals(doc.type()))
                .forEach(doc -> feeds.put(doc.uuid(), doc));
        // The build's newest test feed: one the user named (create_feed test_feed=true) is made after the server's
        // own.
        final List<WriteGuard.BuildDoc> named = new ArrayList<>();
        BuildRecords.docs(records.load(build)).forEach((uuid, entry) -> {
            if (entry instanceof final Map<?, ?> map && JsonValues.truthy(map.get(BuildFeeds.OUTPUT_FOR))
                && feeds.containsKey(uuid)) {
                named.add(feeds.get(uuid));
            }
        });
        final String proposed = sources.getFirst() + TEST_FEED;
        DocRef found = named.isEmpty()
                ? feeds.values().stream()
                        .filter(doc -> doc.name().toLowerCase(Locale.ROOT).equals(proposed.toLowerCase(Locale.ROOT)))
                        .findFirst()
                        .map(doc -> new DocRef(FeedDoc.TYPE, doc.uuid(), doc.name()))
                        .orElse(null)
                : new DocRef(FeedDoc.TYPE, named.getLast().uuid(), named.getLast().name());
        boolean made = false;
        if (found == null) {
            // Seen in Ask Stroom AI: an earlier build's test feed held the name, and every filter failed on Stroom's
            // 500.
            final FeedDoc feed = buildFeeds.makeTestFeed(build, freeFeedName(proposed), sources);
            found = new DocRef(FeedDoc.TYPE, feed.getUuid(), feed.getName());
            made = true;
        }
        PipelineDoc doc = pipelineDocs.read(pipeline.getUuid());
        final Set<String> have = new LinkedHashSet<>();
        for (final PipelineProperty property : PipelineDocs.addedProperties(doc)) {
            if (FEED.equals(property.getName()) && appenders.contains(property.getElement())
                && Objects.equals(NullSafe.get(property.getValue(), PipelinePropertyValue::getEntity,
                    DocRef::getUuid), found.getUuid())) {
                have.add(property.getElement());
            }
        }
        if (!have.containsAll(appenders)) {
            for (final String element : appenders) {
                doc = PipelineDocs.withProperty(doc, element, FEED, new PipelinePropertyValue(found));
            }
            pipelineDocs.write(doc);
        }
        return Optional.of(new OutputFeed(found.getName(), found.getUuid(), made, List.copyOf(sources)));
    }

    /// Takes a promoted pipeline's StreamAppender feed property off where it names one of the build's feeds: in
    /// production its Events go into the input's feed, as Stroom does by default (`restore_output_feed`).
    ///
    /// @param pipelineUuid The pipeline.
    /// @param buildFeeds   The UUIDs of the build's feeds.
    /// @return True if it changed.
    boolean restoreOutputFeed(final String pipelineUuid, final Set<String> buildFeeds) {
        final List<String> appenders = appenders(pipelineUuid);
        final PipelineDoc doc = pipelineDocs.read(pipelineUuid);
        final List<PipelineProperty> added = PipelineDocs.addedProperties(doc);
        final List<PipelineProperty> kept = added.stream()
                .filter(property -> !(appenders.contains(property.getElement())
                                      && FEED.equals(property.getName())
                                      && buildFeeds.contains(NullSafe.get(property.getValue(),
                        PipelinePropertyValue::getEntity, DocRef::getUuid))))
                .toList();
        if (kept.size() == added.size()) {
            return false;
        }
        pipelineDocs.write(PipelineDocs.withAddedProperties(doc, kept));
        return true;
    }

    // The ids of the pipeline's StreamAppenders, as Stroom runs it.
    private List<String> appenders(final String pipelineUuid) {
        final List<String> ids = new ArrayList<>();
        pipelineShapes.merged(pipelineUuid).elements().forEach((id, type) -> {
            if (STREAM_APPENDER.equals(type)) {
                ids.add(id);
            }
        });
        return ids;
    }


    // --------------------------------------------------------------------------------


    /// The build's test feed a pipeline writes its Events to.
    ///
    /// @param name      The feed's name.
    /// @param uuid      Its UUID.
    /// @param made      True if it was made now.
    /// @param worksFrom The existing feeds whose streams the pipeline processes.
    record OutputFeed(String name, String uuid, boolean made, List<String> worksFrom) {

        /// @return It as the Stroom MCP server gave it.
        Map<String, Object> toMap() {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("uuid", uuid);
            map.put("made", made);
            map.put("works_from", worksFrom);
            return map;
        }
    }
}
