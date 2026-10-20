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
import java.util.Objects;

/// Reads the Elasticsearch index template that applies to an Elastic Index document's index, and the component
/// templates it is composed of, as the user. It only reads: nothing in Elasticsearch is changed. Implemented by
/// Stroom's Elasticsearch module, which needs Read on the Elastic Index document and reads the templates with the
/// document's cluster connection, as a search does.
public interface ElasticIndexTemplates {

    /// Reads the templates that apply to an Elastic Index document's index.
    ///
    /// @param index The Elastic Index document.
    /// @return The templates, each part with its own outcome, as one part can be refused by Elasticsearch while
    /// the others are read.
    /// @throws ReadException if nothing could be read: the document doesn't exist or the user may not read it,
    /// it has no index name or cluster, or its cluster can't be reached.
    IndexTemplates read(DocRef index);


    // --------------------------------------------------------------------------------


    /// The outcome of reading one part from Elasticsearch.
    enum Status {
        /// The part was read.
        OK,
        /// No index template matches, or a component template of that name does not exist.
        NOT_FOUND,
        /// Elasticsearch refused, for want of privileges. The message is Elasticsearch's own, which names the
        /// privilege.
        FORBIDDEN,
        /// Anything else, with its message.
        ERROR
    }


    // --------------------------------------------------------------------------------


    /// Why nothing could be read.
    enum Reason {
        /// The document doesn't exist, or the user may not read it, which are not told apart.
        NOT_FOUND,
        /// The document has no index name, or no cluster.
        NOT_CONFIGURED,
        /// The document's cluster doesn't exist, or can't be reached.
        CLUSTER_UNAVAILABLE
    }


    // --------------------------------------------------------------------------------


    /// The templates that apply to an Elastic Index document's index.
    ///
    /// @param indexName          The document's index name, which may be a pattern or an alias.
    /// @param resolvedName       The newest concrete index or data stream the index name stands for, or null if
    ///                           nothing resolved or resolving was refused.
    /// @param resolveMessage     Why the index name could not be resolved, if resolving failed, else null.
    /// @param indexTemplate      The index template that applies.
    /// @param componentTemplates The component templates the index template is composed of, in its order. Empty
    ///                           unless the index template was read.
    record IndexTemplates(String indexName,
                          String resolvedName,
                          String resolveMessage,
                          IndexTemplate indexTemplate,
                          List<ComponentTemplate> componentTemplates) {

        /// @param indexName          The document's index name.
        /// @param resolvedName       The concrete index or data stream it stands for, or null.
        /// @param resolveMessage     Why it could not be resolved, or null.
        /// @param indexTemplate      The index template that applies.
        /// @param componentTemplates The component templates it is composed of; null is taken as none.
        public IndexTemplates {
            componentTemplates = componentTemplates == null
                    ? List.of()
                    : List.copyOf(componentTemplates);
        }
    }


    // --------------------------------------------------------------------------------


    /// The index template that applies: of the templates whose index patterns match, the one with the highest
    /// priority.
    ///
    /// @param status      Whether it was read.
    /// @param name        The template's name, when one matched, else null.
    /// @param body        The template, as JSON, as `GET _index_template` returns it, when one matched, else null.
    /// @param overlapping The other matching templates, highest priority first, which do not apply.
    /// @param message     Why it could not be read, when it was not, else null.
    record IndexTemplate(Status status,
                         String name,
                         String body,
                         List<OverlappingTemplate> overlapping,
                         String message) {

        /// @param status      Whether it was read; required.
        /// @param name        The template's name, or null.
        /// @param body        The template as JSON, or null.
        /// @param overlapping The other matching templates; null is taken as none.
        /// @param message     Why it could not be read, or null.
        public IndexTemplate {
            Objects.requireNonNull(status, "status");
            overlapping = overlapping == null
                    ? List.of()
                    : List.copyOf(overlapping);
        }
    }


    // --------------------------------------------------------------------------------


    /// A template that also matches the index name but has a lower priority, so does not apply.
    ///
    /// @param name          The template's name.
    /// @param indexPatterns The template's index patterns.
    /// @param priority      The template's priority, 0 if it sets none, as Elasticsearch treats it.
    record OverlappingTemplate(String name, List<String> indexPatterns, long priority) {

        /// @param name          The template's name.
        /// @param indexPatterns Its index patterns; null is taken as none.
        /// @param priority      Its priority.
        public OverlappingTemplate {
            indexPatterns = indexPatterns == null
                    ? List.of()
                    : List.copyOf(indexPatterns);
        }
    }


    // --------------------------------------------------------------------------------


    /// A component template the index template is composed of.
    ///
    /// @param name    The component template's name, as the index template's `composed_of` gives it.
    /// @param status  Whether it was read.
    /// @param body    The component template, as JSON, as `GET _component_template` returns it, when read, else
    ///                null.
    /// @param message Why it could not be read, when it was not, else null.
    record ComponentTemplate(String name, Status status, String body, String message) {

        /// @param name    The component template's name.
        /// @param status  Whether it was read; required.
        /// @param body    It as JSON, or null.
        /// @param message Why it could not be read, or null.
        public ComponentTemplate {
            Objects.requireNonNull(status, "status");
        }
    }


    // --------------------------------------------------------------------------------


    /// Thrown when nothing could be read, with why.
    class ReadException extends RuntimeException {

        private final Reason reason;

        /// @param reason  Why nothing could be read.
        /// @param message What went wrong, for the user.
        public ReadException(final Reason reason, final String message) {
            super(message);
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        /// @return Why nothing could be read.
        public Reason getReason() {
            return reason;
        }
    }
}
