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

import stroom.ai.api.ElasticIndexPresence.Presence;
import stroom.ai.api.ElasticIndexPresence.State;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.Globs;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.indexing.IndexNamePatterns;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/// Which Elasticsearch index names are production's, decided by the name on the cluster, not by where an Elastic
/// Index doc lives: a name that a pipeline outside the build writes to, or that an Elastic Index doc outside the
/// build names, is production's (an index name's `{variables}` taken as any value). A template pipeline claims
/// nothing (it writes nothing itself); a build's working copy or new version is also claimed by its original's own
/// index name, inherited from a template or not. A build never processes into
/// such an index, and the tools never put one up for dropping. Seen in a user's test: a promoted feed's indexing
/// pipeline was copied into a build under the same names, the build's own Elastic Index doc for that name made the
/// index count as the build's, and the user was asked to drop the production index before indexing the sample (and
/// did). Only read: nothing in Elasticsearch is changed.
///
/// A pipeline's name is taken with the variables filled in whose values are known for it ([IndexNameValues]): a
/// `{feed}` its indexing XSLT writes as the feed's name is one name for each feed it processes (a production
/// pipeline's processor filters; a build pipeline's build's feeds), so two children of a `{feed}-events` template
/// for different feeds claim different names, and a build pipeline for a feed that already has one is claimed by
/// it. A child inheriting its index name from a template claims that name as its own.
///
/// The same names stop a new version's index name being one that is taken ([#taken]): a name that matches anything
/// on the cluster, even an empty index, or that can't be looked up, is no new version's.
class ProductionIndexes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProductionIndexes.class);

    private static final String INDEX_NAME = "indexName";
    // The most Elastic Index docs read.
    private static final int MAX_ELASTIC_INDEXES = 1_000;
    // The most next names tried for a free one.
    private static final int FREE_NAMES_TRIED = 5;
    // How far up a pipeline's parents an inherited index name is looked for.
    private static final int MAX_INHERITANCE_DEPTH = 10;

    private final PipelineIndex pipelineIndex;
    private final DocumentAccess documentAccess;
    private final WriteGuard guard;
    private final ExistingIndexes existingIndexes;
    private final PipelineTemplates templates;
    private final BuildRecords records;
    private final IndexTemplateAgreements agreements;
    private final PipelineFeeds pipelineFeeds;

    /// @param pipelineIndex   Lists the pipelines and Elastic Index docs the user can view.
    /// @param documentAccess  Reads the Elastic Index docs, as the user.
    /// @param guard           Says which documents are in a build.
    /// @param existingIndexes Looks an index name up on its cluster.
    /// @param templates       Says which pipelines are templates (they write nothing themselves).
    /// @param records         Says what a build's pipeline is a working copy or new version of.
    /// @param agreements      Reads where an original pipeline writes, its template's index name included, and the
    ///                        XSLTs a pipeline runs.
    /// @param pipelineFeeds   Reads the feeds a pipeline's processor filters process.
    @Inject
    ProductionIndexes(final PipelineIndex pipelineIndex,
                      final DocumentAccess documentAccess,
                      final WriteGuard guard,
                      final ExistingIndexes existingIndexes,
                      final PipelineTemplates templates,
                      final BuildRecords records,
                      final IndexTemplateAgreements agreements,
                      final PipelineFeeds pipelineFeeds) {
        this.pipelineIndex = pipelineIndex;
        this.documentAccess = documentAccess;
        this.guard = guard;
        this.existingIndexes = existingIndexes;
        this.templates = templates;
        this.records = records;
        this.agreements = agreements;
        this.pipelineFeeds = pipelineFeeds;
    }

    /// @param build     A build, whose own documents are left out; null to leave none out.
    /// @param indexName An Elasticsearch index name, perhaps with variables, e.g. `acme-events-{year}`.
    /// @return Each pipeline outside the build whose indexName writes to an index the name matches, and each Elastic
    /// Index doc outside the build naming one; none when the name is no one else's.
    List<Claim> claims(final String build, final String indexName) {
        return matching(outside(buildDocs(build)), List.of(indexName));
    }

    /// As [#claims(String, String)], for documents a promotion has just moved out of a build: what else writes to,
    /// or names, the index.
    ///
    /// @param excluded  The UUIDs of the documents left out: those the promotion moved.
    /// @param indexName An Elasticsearch index name, perhaps with variables.
    /// @return Each other pipeline writing to an index the name matches, and each other Elastic Index doc naming
    /// one.
    List<Claim> claimsBesides(final Set<String> excluded, final String indexName) {
        return matching(outside(excluded), List.of(indexName));
    }

    /// As [#claimsBesides(Set, String)], for the index name of a pipeline the promotion moved, with its variables
    /// filled in for the feeds it processes ([IndexNameValues]).
    ///
    /// @param excluded     The UUIDs of the documents left out.
    /// @param pipelineUuid The promoted pipeline.
    /// @param indexName    Its index name, perhaps with variables.
    /// @return Each other pipeline writing to an index the name matches, and each other Elastic Index doc naming
    /// one.
    List<Claim> claimsBesides(final Set<String> excluded, final String pipelineUuid, final String indexName) {
        return matching(outside(excluded), filledFor(pipelineUuid, indexName, Set.of()));
    }

    // A build pipeline's claims: those outside the build, and, for a working copy or new version, its original's
    // own index name, inherited from its template included (a template's {feed}-events, say, writes the original's
    // feed's index, which the copy of the original writes too).
    private List<Claim> claimsOf(final String build, final PipelineDoc pipeline, final String indexName) {
        final List<Claim> claims = new ArrayList<>(matching(outside(buildDocs(build)),
                filledFor(pipeline.getUuid(), indexName, buildFeeds(build))));
        final Map<String, Object> entry = records.entry(build, pipeline.getUuid());
        for (final String key : List.of("copy_of", "version_of")) {
            final String original = NullSafe.get(entry.get(key), String::valueOf);
            if (original == null || claims.stream().anyMatch(claim -> original.equals(claim.doc().getUuid()))) {
                continue;
            }
            try {
                agreements.elasticDestination(original)
                        .map(ProductionIndexes::indexName)
                        .filter(written -> written.equalsIgnoreCase(indexName))
                        .ifPresent(written -> claims.add(new Claim(new DocRef(PipelineDoc.TYPE, original,
                                NullSafe.nonBlankStringElse(pipelineName(original), original)), written)));
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> LogUtil.message("Original pipeline {} not read: {}", original, e.getMessage()));
            }
        }
        return claims;
    }

    /// Refuses a build pipeline's Elasticsearch destination that is production's ([#claims]): before anything is
    /// looked up on the cluster, asked or processed.
    ///
    /// @param pipeline    A pipeline.
    /// @param destination Its Elasticsearch destination ([IndexTemplateAgreements#elasticDestination]).
    /// @throws ToolRefusal for a pipeline in a build whose index name is production's.
    void refuseProduction(final PipelineDoc pipeline, final Map<String, Object> destination) {
        final String index = indexName(destination);
        final String build = guard.buildOf(new DocRef(PipelineDoc.TYPE, pipeline.getUuid())).orElse(null);
        if (index == null || build == null) {
            return;
        }
        final List<Claim> claims = claimsOf(build, pipeline, index);
        if (!claims.isEmpty()) {
            throw new ToolRefusal(productionRefusal(pipeline.getName(), index, claims));
        }
    }

    /// What propose_index_template says, in place of the existing-index warning, of a destination that is
    /// production's: never that the cluster admin drops it.
    ///
    /// @param pipelineUuid The build's pipeline.
    /// @param destination  Its Elasticsearch destination.
    /// @return The warning; empty when the index name is no one else's.
    Optional<String> productionWarning(final String pipelineUuid, final Map<String, Object> destination) {
        final String index = indexName(destination);
        final String build = guard.buildOf(new DocRef(PipelineDoc.TYPE, pipelineUuid)).orElse(null);
        if (index == null || build == null) {
            return Optional.empty();
        }
        final List<Claim> claims = documentAccess.read(new DocRef(PipelineDoc.TYPE, pipelineUuid), PipelineDoc.class)
                .map(pipeline -> claimsOf(build, pipeline, index))
                .orElseGet(() -> claims(build, index));
        if (claims.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("'" + index + "' is a production index (" + said(claims) + "). It is never dropped or "
                           + "emptied for a build, and the build doesn't index into it: don't ask the user or the "
                           + "cluster admin to drop it, and don't commit a template under this name (it would "
                           + "change production's own). A change to its pipeline is a new version with an index "
                           + "name of its own (copy_pipeline proposes one, e.g. '"
                           + VersionNames.nextIndexName(index) + "'), whose template is agreed for that name.");
    }

    /// Whether an index name is taken, for a new version's index: anything it matches on the cluster (a data
    /// stream, an index or an alias, even an empty one), a lookup that fails (an unknown name is never taken as
    /// free), or another pipeline or Elastic Index doc outside the build ([#claims]).
    ///
    /// In Stroom the candidate is compared with its variables filled in as for [#claims] ([IndexNameValues]): with
    /// the new version's XSLTs and the build's feeds (and, for a pipeline in the build, its own filters' feeds), so
    /// a production `{feed}-events-v2` for feed A doesn't take the name from a new version for feed B. The cluster
    /// lookup still takes every variable as any value.
    ///
    /// @param build        The build the new version is made in.
    /// @param pipelineUuid The pipeline whose XSLTs the new version runs (itself, or the original it is copied
    ///                     from), or null for none known.
    /// @param destination  The destination: its cluster and the candidate index name.
    /// @param nothingDone  What the refusal says was not done, e.g. `Nothing was copied.`
    /// @return Why it is taken, with what matched, the pattern looked up and the next free name; empty when it is
    /// free.
    Optional<String> taken(final String build,
                           final String pipelineUuid,
                           final Map<String, Object> destination,
                           final String nothingDone) {
        final String index = indexName(destination);
        if (index == null) {
            return Optional.empty();
        }
        final Set<String> own = buildDocs(build);
        final List<Claim> all = outside(own);
        final Function<String, List<String>> fill = name -> newVersionNames(build, own, pipelineUuid, name);
        final List<String> matched = matched(destination, index, all, fill);
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        final String free = freeName(destination, index, all, fill);
        return Optional.of("Index name '" + index + "' (looked up as '" + ExistingIndexes.lookedUp(index) + "') is "
                           + "not free for the new version: " + String.join("; ", matched) + ". " + nothingDone
                           + " A new version's index is its own, never one that exists or that something else "
                           + "names, even an empty one, and nothing it matches is to be deleted for it. "
                           + (free == null
                ? "No free name was found among the next " + FREE_NAMES_TRIED + " versions: ask the user for one."
                : "'" + free + "' is free: propose it to the user (or another name of theirs)."));
    }

    /// Whether a build pipeline's index name writes to, or is named as, the index a claim's name stands for.
    ///
    /// @param claim     A claim outside the build.
    /// @param indexName The build's index name, perhaps with variables.
    /// @return True if they meet.
    static boolean matches(final Claim claim, final String indexName) {
        return matches(claim, List.of(indexName));
    }

    /// Whether any of the names a build pipeline writes to meets any of those a claim's document stands for.
    ///
    /// @param claim A claim outside the build.
    /// @param ours  The build's index names, each perhaps with variables (those not filled in).
    /// @return True if they meet.
    static boolean matches(final Claim claim, final List<String> ours) {
        final boolean indexDoc = ElasticIndexDoc.TYPE.equals(claim.doc().getType());
        return claim.filled().stream().anyMatch(theirs -> ours.stream()
                .anyMatch(our -> meet(ExistingIndexes.lookedUp(theirs).toLowerCase(Locale.ROOT),
                        ExistingIndexes.lookedUp(our).toLowerCase(Locale.ROOT), indexDoc)));
    }

    private static boolean meet(final String theirs, final String ours, final boolean indexDoc) {
        if (ours.equals(theirs)) {
            return true;
        }
        if (indexDoc && theirs.contains("*")) {
            // An Elastic Index doc naming a pattern searches across indices (every version of a feed's, say): it
            // names no one index for its own.
            return false;
        }
        return Globs.matches(theirs, ours) || Globs.matches(ours, theirs);
    }

    /// @param pipelineUuid A pipeline.
    /// @param indexName    Its index name, perhaps with variables.
    /// @param extraFeeds   Feeds it processes beside its processor filters' (a build's feeds).
    /// @return The names it writes to, its variables filled in where their values are known ([IndexNameValues]);
    /// the name as it is when it has none, or when its XSLTs or feeds can't be read.
    private List<String> filledFor(final String pipelineUuid, final String indexName, final Set<String> extraFeeds) {
        if (IndexNamePatterns.variables(indexName).isEmpty()) {
            return List.of(indexName);
        }
        try {
            final Set<String> feeds = new TreeSet<>(extraFeeds);
            feeds.addAll(JsonValues.strings(pipelineFeeds.pipelineFeeds(pipelineUuid).get("feeds")));
            return IndexNameValues.filled(indexName, agreements.indexingXslts(pipelineUuid), feeds);
        } catch (final RuntimeException e) {
            // Unread, every variable is any value: the name claims, and is claimed by, more, never less.
            LOGGER.debug(() -> LogUtil.message("Index name {} of pipeline {} not filled in: {}", indexName,
                    pipelineUuid, e.getMessage()));
            return List.of(indexName);
        }
    }

    // A new version's candidate name, filled in with its XSLTs and the build's feeds (a production original's own
    // filters' feeds are not the new version's).
    private List<String> newVersionNames(final String build,
                                         final Set<String> own,
                                         final String pipelineUuid,
                                         final String indexName) {
        if (pipelineUuid == null || IndexNamePatterns.variables(indexName).isEmpty()) {
            return List.of(indexName);
        }
        try {
            final Set<String> feeds = buildFeeds(build);
            if (own.contains(pipelineUuid)) {
                feeds.addAll(JsonValues.strings(pipelineFeeds.pipelineFeeds(pipelineUuid).get("feeds")));
            }
            return IndexNameValues.filled(indexName, agreements.indexingXslts(pipelineUuid), feeds);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Index name {} of pipeline {} not filled in: {}", indexName,
                    pipelineUuid, e.getMessage()));
            return List.of(indexName);
        }
    }

    // The feeds a build works from: its own (test) feeds and the existing feeds it was started on.
    private Set<String> buildFeeds(final String build) {
        final Set<String> feeds = new TreeSet<>();
        try {
            guard.folderContents(build).stream()
                    .filter(doc -> FeedDoc.TYPE.equals(doc.type()))
                    .forEach(doc -> feeds.add(doc.name()));
            feeds.addAll(JsonValues.strings(records.load(build).get(BuildRecords.SOURCE_FEEDS)));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Feeds of build {} not read: {}", build, e.getMessage()));
        }
        return feeds;
    }

    // The UUIDs of a build's documents; none for no build.
    private Set<String> buildDocs(final String build) {
        final Set<String> own = new HashSet<>();
        if (build != null) {
            guard.folderContents(build).forEach(doc -> own.add(doc.uuid()));
        }
        return own;
    }

    // Every pipeline but those left out that sets an index name, of its own or inherited (a template's
    // {feed}-events, say), and every Elastic Index doc but those.
    private List<Claim> outside(final Set<String> own) {
        final List<Claim> claims = new ArrayList<>();
        final Map<String, PipelineIndex.Entry> pipelines = pipelineIndex.read();
        for (final PipelineIndex.Entry entry : pipelines.values()) {
            if (own.contains(entry.uuid())) {
                continue;
            }
            for (final String written : indexNames(entry, pipelines)) {
                claims.add(new Claim(new DocRef(PipelineDoc.TYPE, entry.uuid(), entry.name()), written,
                        filledFor(entry.uuid(), written, Set.of())));
            }
        }
        final List<DocRef> elastic = pipelineIndex.list(ElasticIndexDoc.TYPE);
        for (final DocRef ref : elastic.subList(0, Math.min(MAX_ELASTIC_INDEXES, elastic.size()))) {
            if (own.contains(ref.getUuid())) {
                continue;
            }
            try {
                documentAccess.read(ref, ElasticIndexDoc.class)
                        .filter(doc -> NullSafe.isNonBlankString(doc.getIndexName()))
                        .ifPresent(doc -> claims.add(new Claim(new DocRef(ElasticIndexDoc.TYPE, ref.getUuid(),
                                NullSafe.nonBlankStringElse(doc.getName(), ref.getName())), doc.getIndexName())));
            } catch (final RuntimeException e) {
                // One doc gone or unreadable: the others are still read.
                LOGGER.debug(() -> LogUtil.message("Elastic Index doc {} not read: {}", ref.getUuid(),
                        e.getMessage()));
            }
        }
        return claims;
    }

    // The index names a pipeline sets in its own layer; else those of the nearest parent that sets any.
    private static List<String> indexNames(final PipelineIndex.Entry entry,
                                           final Map<String, PipelineIndex.Entry> pipelines) {
        final Set<String> seen = new HashSet<>();
        PipelineIndex.Entry at = entry;
        for (int depth = 0; at != null && seen.add(at.uuid()) && depth <= MAX_INHERITANCE_DEPTH; depth++) {
            final List<String> names = new ArrayList<>();
            for (final PipelineProperty property : NullSafe.list(NullSafe.get(at.own(),
                    PipelineData::getAddedProperties))) {
                final String written = NullSafe.get(property.getValue(), PipelinePropertyValue::getString);
                if (INDEX_NAME.equals(property.getName()) && NullSafe.isNonBlankString(written)) {
                    names.add(written);
                }
            }
            if (!names.isEmpty()) {
                return names;
            }
            at = at.parentUuid() == null
                    ? null
                    : pipelines.get(at.parentUuid());
        }
        return List.of();
    }

    // The claims matching any of the names, a template's left out: a template writes nothing itself, and its index
    // name (often with variables, {feed}-events) would make every child of it look like production (its children
    // claim it, filled in for their own feeds).
    private List<Claim> matching(final List<Claim> all, final List<String> indexNames) {
        return all.stream()
                .filter(claim -> matches(claim, indexNames))
                .filter(claim -> !PipelineDoc.TYPE.equals(claim.doc().getType()) || !template(claim.doc()))
                .toList();
    }

    private boolean template(final DocRef pipeline) {
        try {
            return templates.templateReason(pipeline.getUuid()) != null;
        } catch (final RuntimeException e) {
            // Not known to be a template: it is taken as writing what it names.
            LOGGER.debug(() -> LogUtil.message("Unable to tell if {} is a template: {}", pipeline.getUuid(),
                    e.getMessage()));
            return false;
        }
    }

    private String pipelineName(final String uuid) {
        return documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class)
                .map(PipelineDoc::getName)
                .orElse(null);
    }

    // What a candidate index name matches, said: in Stroom, then on the cluster.
    private List<String> matched(final Map<String, Object> destination,
                                 final String index,
                                 final List<Claim> all,
                                 final Function<String, List<String>> fill) {
        final List<String> matched = new ArrayList<>();
        final List<Claim> claims = matching(all, fill.apply(index));
        if (!claims.isEmpty()) {
            matched.add("in Stroom, " + said(claims));
        }
        final Presence presence = existingIndexes.check(destination);
        if (presence.state() == State.UNKNOWN) {
            matched.add("it couldn't be looked up on cluster " + destination.get("cluster") + " ("
                        + presence.message() + "), and a name that can't be looked up is never taken as free");
        } else if (presence.state() == State.PRESENT) {
            final List<String> names = new ArrayList<>(presence.dataStreams());
            names.addAll(presence.indices());
            names.addAll(presence.aliases());
            matched.add("on cluster " + destination.get("cluster") + " it matches " + presence.kind() + " ("
                        + String.join(", ", names) + "), holding " + (presence.documents() == null
                    ? "a number of documents that couldn't be counted"
                    : presence.documents() + " document(s)"));
        }
        return matched;
    }

    // The first of the next few versions of the name that matches nothing, or null.
    private String freeName(final Map<String, Object> destination,
                            final String index,
                            final List<Claim> all,
                            final Function<String, List<String>> fill) {
        String candidate = index;
        for (int i = 0; i < FREE_NAMES_TRIED; i++) {
            candidate = VersionNames.nextIndexName(candidate);
            final Map<String, Object> next = new LinkedHashMap<>(destination);
            next.put("index name", candidate);
            if (matching(all, fill.apply(candidate)).isEmpty() && existingIndexes.check(next).state() == State.ABSENT) {
                return candidate;
            }
        }
        return null;
    }

    private static String productionRefusal(final String pipeline, final String index, final List<Claim> claims) {
        return "Pipeline '" + pipeline + "' writes to Elasticsearch index '" + index + "', which is a production "
               + "index (" + said(claims) + "). A build never writes into a production index, nor empties or drops "
               + "one: processing would put the sample's documents among production's. Nothing was processed, and "
               + "nothing is to be dropped or deleted: don't ask the user or the cluster admin to drop it, or to "
               + "commit a template over its own. For an index the build can index into, make a new version of the "
               + "promoted pipeline with copy_pipeline, which proposes an index name of its own (e.g. '"
               + VersionNames.nextIndexName(index) + "') and checks it is free.";
    }

    private static String said(final List<Claim> claims) {
        return String.join("; ", claims.stream().map(Claim::said).distinct().toList());
    }

    private static String indexName(final Map<String, Object> destination) {
        final Object index = destination == null
                ? null
                : destination.get("index name");
        return index == null || String.valueOf(index).isBlank()
                ? null
                : String.valueOf(index).strip();
    }


    // --------------------------------------------------------------------------------


    /// A document outside a build that writes to, or names, an Elasticsearch index name.
    ///
    /// @param doc       The pipeline (its indexName, its own or inherited) or Elastic Index doc.
    /// @param indexName The index name it writes to or names, as set.
    /// @param filled    The names it stands for: a pipeline's with the variables filled in whose values are known
    ///                  for it ([IndexNameValues]), one a feed; else the name itself.
    record Claim(DocRef doc, String indexName, List<String> filled) {

        /// @param doc       The pipeline or Elastic Index doc.
        /// @param indexName The index name it writes to or names, standing for itself.
        Claim(final DocRef doc, final String indexName) {
            this(doc, indexName, List.of(indexName));
        }

        /// @return It, said, e.g. `pipeline 'Acme-Events-V1.0-Indexing' writes to 'acme-events-v1'`, with the names
        /// filled in when they differ (`writes to '{feed}-events' ('acme-events')`).
        String said() {
            if (!PipelineDoc.TYPE.equals(doc.getType())) {
                return "Elastic Index doc '" + doc.getName() + "' names '" + indexName + "'";
            }
            return "pipeline '" + doc.getName() + "' writes to '" + indexName + "'"
                   + (filled.equals(List.of(indexName))
                    ? ""
                    : " (" + String.join(", ", filled.stream().map(name -> "'" + name + "'").toList()) + ")");
        }
    }
}
