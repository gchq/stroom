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

import stroom.ai.api.ElasticIndexPresence;
import stroom.ai.api.ElasticIndexPresence.Presence;
import stroom.ai.api.ElasticIndexPresence.State;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/// Whether the index an Elasticsearch indexing pipeline writes to already exists on its cluster, holding documents,
/// before the pipeline first indexes into it. Seen in testing: a build's first indexing run went into a data stream of
/// the same name that already held documents; only verify_index's document counts showed it, and the agent, taking
/// the counts for a translation fault, went on to hand-write the XSLT. Found first, nothing is processed: the cluster
/// admin drops it, or the user chooses another name. Only read: nothing is written to Elasticsearch.
class ExistingIndexes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExistingIndexes.class);

    /// The details key for what the index name already stands for on the cluster, when it holds no documents or
    /// isn't known.
    static final String EXISTING_INDEX = "existing index";
    /// The status of a reply stopping before processing into an index that already holds documents.
    static final String STATUS = "index_exists";

    // An index name's variables, e.g. `{year}`, filled per document by the indexing filter: looked up as a pattern.
    // The most data streams and indices listed for deletion; the rest are counted.
    private static final int MAX_LISTED = 20;
    private static final Pattern VARIABLE = Pattern.compile("\\{[^}]+?}");

    private final Provider<ElasticIndexPresence> presenceProvider;

    /// @param presenceProvider Looks an index name up on a cluster, as the user.
    @Inject
    ExistingIndexes(final Provider<ElasticIndexPresence> presenceProvider) {
        this.presenceProvider = presenceProvider;
    }

    /// @param destination An Elasticsearch indexing pipeline's destination
    ///                    ([IndexTemplateAgreements#elasticDestination]).
    /// @return What its index name stands for on its cluster; unknown when it names no cluster doc or index name.
    Presence check(final Map<String, Object> destination) {
        final String cluster = text(destination.get(IndexTemplateAgreements.CLUSTER_UUID));
        final String index = text(destination.get("index name"));
        if (cluster == null || index == null) {
            return Presence.unknown("The pipeline names no cluster or index name");
        }
        try {
            return presenceProvider.get().check(new DocRef(ElasticClusterDoc.TYPE, cluster,
                    text(destination.get("cluster"))), lookedUp(index));
        } catch (final RuntimeException e) {
            // Never a reason to stop: the check is a courtesy before processing.
            LOGGER.debug(() -> LogUtil.message("Unable to look '{}' up on cluster {}: {}", index, cluster,
                    e.getMessage()), e);
            return Presence.unknown(NullSafe.nonBlankStringElse(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /// @param index An index name, perhaps with variables, e.g. `ecs-acme-{year}`.
    /// @return It as looked up: each variable a wildcard.
    static String lookedUp(final String index) {
        return VARIABLE.matcher(index).replaceAll("*");
    }

    /// @param presence    What the index name stands for.
    /// @param destination The pipeline's destination.
    /// @return What the approval says of it when processing goes on: that it exists, empty, or holding a count that
    /// couldn't be read; or that it couldn't be checked. Empty when it doesn't exist.
    static Optional<String> note(final Presence presence, final Map<String, Object> destination) {
        final String index = String.valueOf(destination.get("index name"));
        if (presence.state() == State.UNKNOWN) {
            return Optional.of("whether '" + index + "' already exists on cluster " + destination.get("cluster")
                               + " couldn't be checked (" + presence.message() + "); if it does and holds "
                               + "documents, the document counts checked after indexing will include them");
        }
        if (presence.state() == State.ABSENT) {
            return Optional.empty();
        }
        if (presence.documents() == null) {
            return Optional.of("'" + index + "' already exists on cluster " + destination.get("cluster") + " ("
                               + presence.kind() + ") but its documents couldn't be counted (" + presence.message()
                               + "); if it holds any, the document counts checked after indexing will include them");
        }
        return Optional.of("'" + index + "' already exists on cluster " + destination.get("cluster") + " ("
                           + presence.kind() + "), holding no documents: indexing goes into it, with the mapping "
                           + "it was made with");
    }

    /// The reply stopping before the first processing into an index that already holds documents.
    ///
    /// @param pipeline    The indexing pipeline.
    /// @param destination Its destination.
    /// @param presence    What its index name stands for, holding documents.
    /// @param saidDropped True when the call said the index was dropped (`index_dropped=true`), so it is still there.
    /// @param tool        The tool to call again once it is dropped.
    /// @return The `index_exists` reply.
    static Map<String, Object> stop(final PipelineDoc pipeline,
                                    final Map<String, Object> destination,
                                    final Presence presence,
                                    final boolean saidDropped,
                                    final String tool) {
        final String index = String.valueOf(destination.get("index name"));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", STATUS);
        out.put("pipeline", pipeline.getName());
        out.put("index", index);
        out.put("cluster", destination.get("cluster"));
        out.put("exists_as", presence.kind());
        out.put("names", names(presence));
        out.put("documents", presence.documents());
        final String held = "'" + index + "' " + (saidDropped
                ? "still exists"
                : "already exists") + " on cluster " + destination.get("cluster") + " (" + presence.kind()
                            + "), holding " + presence.documents() + " document(s)";
        out.put("problem", (saidDropped
                ? "index_dropped=true was given, but " + held + ": it hasn't been dropped, or something wrote to "
                  + "it since. "
                : held + " this pipeline didn't index. ")
                           + "Indexing into it would mix the sample's documents with its documents, under the "
                           + "mapping it was made with, so verify_index's document counts would fail");
        // A name with variables is looked up with each as a wildcard: any index it matches is one the pipeline could
        // write to, whatever the variables' values.
        if (!lookedUp(index).equals(index)) {
            out.put("looked_up_as", lookedUp(index));
        }
        // Every data stream and index the pipeline could write to, each deleted first by the cluster admin (by hand:
        // Stroom only shows the request).
        final List<String> requests = deleteRequests(presence);
        out.put("delete_first", requests.size() > MAX_LISTED
                ? List.copyOf(requests.subList(0, MAX_LISTED))
                : requests);
        if (requests.size() > MAX_LISTED) {
            out.put("delete_first_more", requests.size() - MAX_LISTED);
        }
        final List<Map<String, Object>> shown = new ArrayList<>();
        for (int n = 0; n < Math.min(requests.size(), MAX_LISTED); n++) {
            final String request = requests.get(n);
            final String name = request.replaceFirst("^DELETE (_data_stream/)?", "");
            shown.add(UserActionEntries.esRequest("delete-first-" + (n + 1), request,
                    "Delete '" + name + "' first, so the build's index starts fresh"));
        }
        UserActionEntries.withEntries(out, shown);
        out.put("hint", "Nothing has been processed. This is not a translation problem: don't change the "
                        + "translation or its XSLT, and don't hand-write one, over this. Tell the user what "
                        + "exists (problem) and list every data stream and index in delete_first"
                        + (requests.size() > MAX_LISTED
                                ? " (and that " + (requests.size() - MAX_LISTED) + " more match "
                                  + out.getOrDefault("looked_up_as", index)
                                : "")
                        + ": those are what the cluster admin deletes by hand before indexing. Ask which they "
                        + "want: (1) the admin deletes them, and they tell you when it is done; then " + tool
                        + " again with the same arguments and index_dropped=true (it is checked again), whose "
                        + "approval follows; or (2) another index name: rename_index, then propose_index_template "
                        + "for the new name, then " + tool + " again. Don't pass index_dropped before they say "
                        + "it is done.");
        return out;
    }

    /// A warning for the agreement of an index template, so the user can choose another name before anything is
    /// indexed.
    ///
    /// @param presence    What the index name stands for.
    /// @param destination The pipeline's destination.
    /// @return The warning, when the name already holds documents; else empty.
    static Optional<String> warning(final Presence presence, final Map<String, Object> destination) {
        if (!presence.holdsDocuments()) {
            return Optional.empty();
        }
        final String index = String.valueOf(destination.get("index name"));
        return Optional.of("'" + index + "' already exists on cluster " + destination.get("cluster") + " ("
                           + presence.kind() + "), holding " + presence.documents() + " document(s), and an "
                           + "existing index keeps the mapping it was made with, not this template's. Tell the "
                           + "user now: before processing, the cluster admin drops it, or they choose another "
                           + "name (rename_index, then propose_index_template again). The first processing run "
                           + "stops until one is done.");
    }

    // The Dev Tools request deleting each data stream and index the name stands for (an alias's among them: deleting
    // them empties it), data streams first.
    private static List<String> deleteRequests(final Presence presence) {
        final List<String> requests = new ArrayList<>();
        presence.dataStreams().forEach(dataStream -> requests.add("DELETE _data_stream/" + dataStream));
        presence.indices().forEach(name -> requests.add("DELETE " + name));
        return requests;
    }

    private static List<String> names(final Presence presence) {
        final List<String> names = new ArrayList<>(presence.dataStreams());
        names.addAll(presence.indices());
        names.addAll(presence.aliases());
        return names;
    }

    private static String text(final Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null
                : String.valueOf(value).strip();
    }
}
