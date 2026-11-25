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

import stroom.ai.impl.tools.ToolText;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// Every pipeline that runs an XSLT, and every feed those pipelines' processor filters read (decided with the user
/// 2026-11-25: a translation inducted in place is surveyed over every feed of every pipeline using it, the count
/// and an estimate stated first). Found live, as Stroom runs the pipelines: those whose own layer names the XSLT,
/// and their children that inherit it, each confirmed by its effective properties (a child that sets another XSLT
/// in its place is not one); never a build's own. Read as the user: pipelines they can view, filters as they may
/// manage processors.
class XsltUsers {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltUsers.class);

    /// The stream type surveyed and counted for each feed.
    static final String RAW_EVENTS = "Raw Events";
    /// About how long one survey call takes, reading its share of a feed: a rough figure for the estimate the user
    /// narrows the survey by, never a promise.
    static final int SECONDS_PER_SURVEY_CALL = 30;

    private final PipelineIndex pipelineIndex;
    private final PipelineShapes pipelineShapes;
    private final Provider<ProcessorFilters> processorFilters;
    private final FeedVolumes feedVolumes;
    private final WriteGuard guard;

    /// @param pipelineIndex    Lists the pipelines the user can view.
    /// @param pipelineShapes   Reads a pipeline as Stroom runs it.
    /// @param processorFilters Reads the processor filters and the feeds they select.
    /// @param feedVolumes      Counts a feed's streams.
    /// @param guard            Tells a build's documents from others.
    @Inject
    XsltUsers(final PipelineIndex pipelineIndex,
              final PipelineShapes pipelineShapes,
              final Provider<ProcessorFilters> processorFilters,
              final FeedVolumes feedVolumes,
              final WriteGuard guard) {
        this.pipelineIndex = pipelineIndex;
        this.pipelineShapes = pipelineShapes;
        this.processorFilters = processorFilters;
        this.feedVolumes = feedVolumes;
        this.guard = guard;
    }

    /// @param xsltUuid An XSLT.
    /// @return Every pipeline outside any build that runs it, with the feeds its filters read, and those feeds'
    /// volumes.
    Users of(final String xsltUuid) {
        final Map<String, PipelineIndex.Entry> index = pipelineIndex.read();
        final Set<String> candidates = new LinkedHashSet<>();
        index.values().stream()
                .filter(entry -> ownLayerNames(entry, xsltUuid))
                .map(PipelineIndex.Entry::uuid)
                .forEach(candidates::add);
        // Their children, and theirs: a child inherits its parent's XSLT unless it sets another.
        boolean more = !candidates.isEmpty();
        while (more) {
            more = false;
            for (final PipelineIndex.Entry entry : index.values()) {
                if (entry.parentUuid() != null && candidates.contains(entry.parentUuid())
                    && candidates.add(entry.uuid())) {
                    more = true;
                }
            }
        }
        final List<ProcessorFilter> filters = processorFilters.get().all();
        final Map<String, Set<String>> feedsByPipeline = processorFilters.get().feedsByPipeline(filters);
        final List<User> users = new ArrayList<>();
        for (final String uuid : candidates) {
            final PipelineIndex.Entry entry = index.get(uuid);
            if (inBuild(uuid) || !runs(uuid, xsltUuid)) {
                continue;
            }
            final boolean hasFilters = filters.stream().anyMatch(filter -> uuid.equals(filter.getPipelineUuid()));
            users.add(new User(new DocRef(PipelineDoc.TYPE, uuid, entry.name()), hasFilters,
                    List.copyOf(NullSafe.set(feedsByPipeline.get(uuid)))));
        }
        final Map<String, FeedVolumes.Volume> volumes = new LinkedHashMap<>();
        for (final User user : users) {
            for (final String feed : user.feeds()) {
                volumes.computeIfAbsent(feed, name -> feedVolumes.volume(name, RAW_EVENTS));
            }
        }
        return new Users(xsltUuid, users, volumes, processorFilters.get().canManageProcessors());
    }

    // Whether the pipeline, as Stroom runs it, sets the XSLT on any element.
    private boolean runs(final String pipelineUuid, final String xsltUuid) {
        try {
            return pipelineShapes.merged(pipelineUuid).translationDocs(pipelineUuid).stream()
                    .anyMatch(doc -> xsltUuid.equals(doc.doc().getUuid()));
        } catch (final PipelineUnreadableException e) {
            LOGGER.debug(() -> LogUtil.message("Pipeline {} can't be read: {}", pipelineUuid, e.getMessage()));
            return false;
        }
    }

    private boolean inBuild(final String uuid) {
        return guard.buildOf(new DocRef(PipelineDoc.TYPE, uuid)).isPresent();
    }

    // Whether a pipeline's own layer names the XSLT.
    private static boolean ownLayerNames(final PipelineIndex.Entry entry, final String uuid) {
        if (entry.own() == null) {
            return false;
        }
        try {
            return JsonUtil.writeValueAsString(entry.own(), false).contains(uuid);
        } catch (final RuntimeException e) {
            return false;
        }
    }


    // --------------------------------------------------------------------------------


    /// A pipeline running the XSLT.
    ///
    /// @param pipeline   The pipeline.
    /// @param hasFilters True if it has processor filters (a template, say, has none).
    /// @param feeds      The feeds its filters read, as written.
    record User(DocRef pipeline, boolean hasFilters, List<String> feeds) {

    }


    // --------------------------------------------------------------------------------


    /// The pipelines running an XSLT and their feeds.
    ///
    /// @param xsltUuid          The XSLT.
    /// @param users             The pipelines, in name order.
    /// @param volumes           Each feed's raw streams, by feed, in the order first read.
    /// @param filtersReadable   False if the user may not manage processors: no feed could be read.
    record Users(String xsltUuid, List<User> users, Map<String, FeedVolumes.Volume> volumes, boolean filtersReadable) {

        /// @return Every feed the pipelines read, each once, in the order first read.
        List<String> feeds() {
            return List.copyOf(volumes.keySet());
        }

        /// @return The raw streams of every feed.
        long streams() {
            return volumes.values().stream().mapToLong(FeedVolumes.Volume::streams).sum();
        }

        /// @return About how many survey calls a first pass over every feed takes: one a feed with streams, and one
        /// more for each [SurveyFeedTool#MAX_CHARS] its streams hold past the first, up to
        /// [SurveyFeedTool#MAX_PARTS] a feed.
        int surveyCalls() {
            int calls = 0;
            for (final FeedVolumes.Volume volume : volumes.values()) {
                if (volume.streams() == 0) {
                    continue;
                }
                final long bytes = volume.bytes() == null
                        ? 0
                        : volume.bytes();
                final long more = bytes / SurveyFeedTool.MAX_CHARS;
                calls += (int) Math.min(SurveyFeedTool.MAX_PARTS, 1 + more);
            }
            return calls;
        }

        /// @return The count and estimate in words, for the user to narrow the survey by: the pipelines, each feed
        /// with its streams, and about how many survey calls and minutes surveying them all takes.
        String said() {
            final List<String> feedsSaid = volumes.values().stream().map(FeedVolumes.Volume::said).toList();
            final int calls = surveyCalls();
            final long minutes = Math.max(1, Math.round(calls * SECONDS_PER_SURVEY_CALL / 60.0));
            final List<String> named = users.stream().map(user -> ToolText.repr(user.pipeline().getName()))
                    .toList();
            return "It runs in " + users.size() + " pipeline" + (users.size() == 1
                    ? ""
                    : "s") + " (" + String.join(", ", named) + "), whose filters read " + volumes.size() + " feed"
                   + (volumes.size() == 1
                    ? ""
                    : "s") + ": " + (feedsSaid.isEmpty()
                    ? "none"
                    : String.join("; ", feedsSaid)) + ". Surveying " + (volumes.size() == 1
                    ? "it"
                    : "them all") + " takes about " + calls + " survey call" + (calls == 1
                    ? ""
                    : "s") + " (roughly " + minutes + " minute" + (minutes == 1
                    ? ""
                    : "s") + "), more until each is covered.";
        }

        /// @param chosen The feeds the user chose, by name in any case.
        /// @return Those of them the pipelines read, as written; the rest left out.
        List<String> narrowedTo(final List<String> chosen) {
            final Set<String> wanted = new LinkedHashSet<>();
            NullSafe.list(chosen).forEach(feed -> wanted.add(NullSafe.string(feed).strip().toLowerCase(Locale.ROOT)));
            return feeds().stream().filter(feed -> wanted.contains(feed.toLowerCase(Locale.ROOT))).toList();
        }

        /// @param text What the user typed.
        /// @return The feeds the pipelines read that it names, as written, in their order.
        List<String> namedIn(final String text) {
            final String said = NullSafe.string(text).toLowerCase(Locale.ROOT);
            return feeds().stream()
                    // A whole name: no name character either side, a full stop ending the sentence allowed.
                    .filter(feed -> said.matches("(?s).*(?<![\\w.-])" + Pattern.quote(
                            feed.toLowerCase(Locale.ROOT)) + "(?![\\w-])(?!\\.[\\w-]).*"))
                    .toList();
        }
    }
}
