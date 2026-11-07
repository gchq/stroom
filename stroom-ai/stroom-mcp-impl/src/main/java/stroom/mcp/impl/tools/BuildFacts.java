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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolRefusal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What the plan ([BuildPlan]) reads from Stroom about a build: its documents, their streams, its pipelines and what
/// its record says of them. Kept apart from the plan's own logic, which works only from these, so that logic is tested
/// on its own. [StroomBuildFacts] reads them from Stroom, as the user.
///
/// A build's documents are maps: `type`, `uuid`, `name`, `path`, `working_copy_of`, `version_of` and `role`.
interface BuildFacts {

    /// @param build A build's name.
    /// @return Its documents (not its record), in a stable order: by type, name and UUID.
    List<Map<String, Object>> buildDocs(String build);

    /// @param docs A build's documents.
    /// @return Whether classify_kinds has settled every kind of event: its source notes say none is still to classify.
    boolean classified(List<Map<String, Object>> docs);

    /// @param build A build's name.
    /// @param docs  Its documents.
    /// @return The raw streams on its feeds, by stream type (`Raw Events`, `Raw Reference`): each stream's `id`,
    /// `feed`, `createMs` and `status`; only each feed's own (created since the feed was).
    Map<String, List<Map<String, Object>>> sampleStreams(String build, List<Map<String, Object>> docs);

    /// @param streamId A raw stream.
    /// @return Its head (the first 20,000 characters or so), cut at the end of its last whole line.
    /// @throws ToolRefusal      if its text can't be read (e.g. the user doesn't have the View Data permission).
    /// @throws RuntimeException if it can't be read otherwise (e.g. it is gone).
    String streamHead(long streamId);

    /// @param text Sample text.
    /// @return Its profile ([SampleProfiling#profile]).
    /// @throws ToolRefusal if it can't be profiled.
    Map<String, Object> profile(String text);

    /// @param xsltUuid An XSLT.
    /// @return The mapping kept in its description, or empty (none kept, or it can't be read).
    Optional<KeptMapping.Kept> xsltMapping(String xsltUuid);

    /// @param pipelineUuid A pipeline.
    /// @return Its stage and parser.
    /// @throws PipelineUnreadableException if Stroom can't load it.
    PipelineShapes.Shape pipelineShape(String pipelineUuid);

    /// @param pipelineUuid A pipeline.
    /// @return The properties it leaves unset that its chain needs to run (its parser's text converter, its first
    /// XSLT step's xslt), as `element.property`.
    /// @throws PipelineUnreadableException if Stroom can't load it.
    List<String> openSlots(String pipelineUuid);

    /// @param pipeline One of the build's pipelines.
    /// @return Whether its saved code has stepped clean.
    boolean steppedClean(Map<String, Object> pipeline);

    /// @param pipeline One of the build's indexing pipelines.
    /// @return Whether verify_index passed for its current code.
    boolean verified(Map<String, Object> pipeline);

    /// @param pipeline One of the build's pipelines.
    /// @return Whether Events from its current code passed check_events.
    boolean validated(Map<String, Object> pipeline);

    /// @param type A document's type.
    /// @param uuid Its UUID.
    /// @return Its name, if the user can read it.
    Optional<String> docName(String type, String uuid);

    /// The Events streams of one of the build's feeds that show what its translation writes now: not made from a raw
    /// stream since deleted, and, where the build's translation pipelines made any, theirs, made since the
    /// pipeline's XSLT was last saved. Seen in testing: a build's documentation step named the Events of an earlier
    /// run, whose raw stream had been deleted.
    ///
    /// @param feed         One of the build's feeds.
    /// @param translations The UUIDs of the build's translation pipelines.
    /// @return Those Events streams, and the ones left out.
    EventsStreams eventsStreams(Map<String, Object> feed, List<String> translations);

    /// The fields processing reported as read by no rule of the build's translation (its Error streams' "No rule reads
    /// field" lines), less those its XSLT names now (mapped, or listed in ignored_fields): each for the user to have
    /// mapped (then the streams holding it reprocessed) or left out. Asked for by the user: the build doesn't go on
    /// with fields silently unmapped.
    ///
    /// @param events       The build's current Events streams: their raw streams (its samples, or an existing feed's)
    ///                     are where processing reported them.
    /// @param translations The UUIDs of the build's translation pipelines.
    /// @return Each such field, with the raw streams it was reported in, in the order found.
    Map<String, List<Long>> unmappedFields(List<Long> events, List<String> translations);

    /// @param pipelineUuid An indexing pipeline.
    /// @return Where an Elasticsearch indexing pipeline writes (`index name`, `cluster`); empty for others.
    Optional<Map<String, Object>> elasticDestination(String pipelineUuid);

    /// @param pipelineUuid An Elasticsearch indexing pipeline.
    /// @param destination  Where it writes.
    /// @return Why it has no index template agreed with the user for its current code, or empty.
    Optional<String> agreementProblem(String pipelineUuid, Map<String, Object> destination);

    /// @param docs A build's documents.
    /// @return What its pipelines still lack before promotion ([BuildChecks]).
    List<String> buildChecks(List<Map<String, Object>> docs);

    /// @param build A build's name.
    /// @param docs  Its documents.
    /// @return The existing feeds it works from, rather than samples it uploads: those start_build was given
    /// (outside the build), the feed a survey read, and the feeds the production pipelines it copies process.
    /// Never the build's own feeds: in such a build those are test feeds.
    List<String> sourceFeeds(String build, List<Map<String, Object>> docs);

    /// @param surveys The build's survey docs.
    /// @return What they say (`saturated`, `format`, `locations`, `streams`, `sample`), or empty when none can be
    /// read.
    Optional<Map<String, Object>> surveyState(List<Map<String, Object>> surveys);

    /// @param pipelineUuid A pipeline.
    /// @return Its processor filters, as processing_status gives them (each with `enabled` and `finished`).
    List<Map<String, Object>> processingFilters(String pipelineUuid);

    /// @param pipelineUuid An indexing pipeline.
    /// @return The dashboard columns to suggest for its verification, from its index plan.
    List<String> suggestedColumns(String pipelineUuid);

    /// @param pipelineUuid A pipeline.
    /// @return The UUID of its own XSLT that keeps its mapping, or empty.
    Optional<String> keptXsltUuid(String pipelineUuid);

    /// @param feeds Feeds' names.
    /// @return The pipelines translating their raw data now (their processor filters read it): each `name` and
    /// `uuid`.
    List<Map<String, Object>> translating(List<String> feeds);

    /// @param build A build.
    /// @return The kind of work it was started as (`events`, `discovery` or `reference`), or empty when none was
    /// named.
    Optional<String> kind(String build);

    /// @param feed A feed's name.
    /// @return Its Reference streams (a reference-data pipeline's output), newest first; empty when it has none.
    List<Long> referenceStreams(String feed);

    /// @param pipelineUuid A pipeline.
    /// @return The feeds its own pipeline references load reference data from, by name.
    List<String> referencedFeeds(String pipelineUuid);


    // --------------------------------------------------------------------------------


    /// A feed's Events streams for the plan.
    ///
    /// @param current The ids of those showing what the translation writes now.
    /// @param leftOut The ids of the others: made from a raw stream since deleted, by another pipeline, or before the
    ///                XSLT was last saved.
    record EventsStreams(List<Long> current, List<Long> leftOut) {

    }
}
