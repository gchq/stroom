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

package stroom.ai.api;

import stroom.docref.DocRef;

import java.util.List;

/// Says whether an index name already stands for something on an Elastic Cluster (a data stream, an index or an
/// alias), and how many documents it holds, as the user: before a build indexes into a name, so one that already
/// holds documents is found before processing rather than by the document counts after it. It only reads: nothing in
/// Elasticsearch is changed. Implemented by Stroom's Elasticsearch module, which needs Read on the Elastic Cluster
/// document and reads with its connection.
public interface ElasticIndexPresence {

    /// Looks an index name up on a cluster. Never fails for Elasticsearch's sake: a cluster that can't be reached, a
    /// request it refuses or an answer that can't be read is [State#UNKNOWN], never [State#ABSENT].
    ///
    /// @param cluster   The Elastic Cluster document, as an indexing pipeline's `cluster` property names it.
    /// @param indexName The index name, as an indexing pipeline's `indexName` property gives it.
    /// @return What the name stands for on the cluster, or why that isn't known.
    Presence check(DocRef cluster, String indexName);


    // --------------------------------------------------------------------------------


    /// Whether an index name stands for anything on a cluster.
    enum State {
        /// Nothing of that name: indexing makes it (as the cluster's templates say).
        ABSENT,
        /// A data stream, an index or an alias of that name.
        PRESENT,
        /// Not known: the cluster document can't be read, the cluster can't be reached, or it refused or gave an
        /// answer that can't be read.
        UNKNOWN
    }


    // --------------------------------------------------------------------------------


    /// What an index name stands for on a cluster.
    ///
    /// @param state       Whether it stands for anything.
    /// @param dataStreams The data streams it names; empty if none.
    /// @param indices     The indices it names, other than a data stream's backing indices; empty if none.
    /// @param aliases     The aliases it names; empty if none.
    /// @param documents   How many documents it holds, or null when not present or the count couldn't be read.
    /// @param message     Why the state, or the count, isn't known; else null.
    record Presence(State state,
                    List<String> dataStreams,
                    List<String> indices,
                    List<String> aliases,
                    Long documents,
                    String message) {

        /// @param state       Whether it stands for anything.
        /// @param dataStreams The data streams; null is taken as none.
        /// @param indices     The indices; null is taken as none.
        /// @param aliases     The aliases; null is taken as none.
        /// @param documents   How many documents, or null.
        /// @param message     Why something isn't known, or null.
        public Presence {
            dataStreams = dataStreams == null
                    ? List.of()
                    : List.copyOf(dataStreams);
            indices = indices == null
                    ? List.of()
                    : List.copyOf(indices);
            aliases = aliases == null
                    ? List.of()
                    : List.copyOf(aliases);
        }

        /// @return Nothing of that name on the cluster.
        public static Presence absent() {
            return new Presence(State.ABSENT, List.of(), List.of(), List.of(), null, null);
        }

        /// @param message Why it isn't known.
        /// @return Not known, with why.
        public static Presence unknown(final String message) {
            return new Presence(State.UNKNOWN, List.of(), List.of(), List.of(), null, message);
        }

        /// @return True when the name stands for something holding at least one document.
        public boolean holdsDocuments() {
            return state == State.PRESENT && documents != null && documents > 0;
        }

        /// @return What the name stands for, in words: `a data stream`, `an index`, `an alias` (or several of
        /// them, e.g. `an alias and an index`); empty when not present.
        public String kind() {
            final StringBuilder kind = new StringBuilder();
            appendKind(kind, dataStreams, "a data stream", "data streams");
            appendKind(kind, indices, "an index", "indices");
            appendKind(kind, aliases, "an alias", "aliases");
            return kind.toString();
        }

        private static void appendKind(final StringBuilder kind,
                                       final List<String> names,
                                       final String one,
                                       final String several) {
            if (names.isEmpty()) {
                return;
            }
            if (!kind.isEmpty()) {
                kind.append(" and ");
            }
            kind.append(names.size() == 1
                    ? one
                    : names.size() + " " + several);
        }
    }
}
