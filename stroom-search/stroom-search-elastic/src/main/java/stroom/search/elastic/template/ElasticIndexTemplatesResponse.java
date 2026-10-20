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

package stroom.search.elastic.template;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/// The index template and component templates that apply to an Elastic Index doc's index, as read from
/// Elasticsearch.
///
/// Each part carries its own [Status], as each Elasticsearch call can be refused on its own, most often
/// because the cluster's credentials lack the privileges to read templates. A part that could not be read
/// does not stop the others being returned.
///
/// The template bodies are Elasticsearch's JSON exactly as its GET APIs return them, so they are written
/// out as raw JSON. This makes the response one to serialise, not to read back.
///
/// @param indexName          The doc's index name, which may be a pattern or an alias.
/// @param resolvedName       The newest concrete index or data stream the index name stands for, or null
///                           if nothing resolved or resolving was refused.
/// @param resolveMessage     Why the index name could not be resolved, if resolving failed.
/// @param indexTemplate      The index template that applies to the resolved name.
/// @param componentTemplates The component templates the index template is composed of, in its order.
///                           Empty unless the index template was read.
public record ElasticIndexTemplatesResponse(
        @JsonProperty("indexName") String indexName,
        @JsonProperty("resolvedName") String resolvedName,
        @JsonInclude(Include.NON_NULL) @JsonProperty("resolveMessage") String resolveMessage,
        @JsonProperty("indexTemplate") IndexTemplate indexTemplate,
        @JsonProperty("componentTemplates") List<ComponentTemplate> componentTemplates) {

    /// The outcome of reading one part from Elasticsearch.
    public enum Status {
        /// The part was read.
        OK,
        /// No index template matches, or a component template of that name does not exist.
        NOT_FOUND,
        /// Elasticsearch refused, for want of privileges. The message is Elasticsearch's own, which
        /// names the privilege.
        FORBIDDEN,
        /// Anything else, with its message.
        ERROR
    }

    /// The index template that applies, by Elasticsearch's own rule: of the templates whose index patterns
    /// match, the one with the highest priority.
    ///
    /// @param status      Whether it was read.
    /// @param name        The template's name, when one matched.
    /// @param body        The template as `GET _index_template` returns it, when one matched.
    /// @param overlapping The other matching templates, highest priority first, which do not apply.
    /// @param message     Why it could not be read, when it was not.
    @Schema(name = "ElasticIndexTemplate")
    @JsonInclude(Include.NON_NULL)
    public record IndexTemplate(
            @JsonProperty("status") Status status,
            @JsonProperty("name") String name,
            @Schema(implementation = Object.class, description = "The template as GET _index_template returns it")
            @JsonRawValue @JsonProperty("body") String body,
            @JsonProperty("overlapping") List<OverlappingTemplate> overlapping,
            @JsonProperty("message") String message) {

        /// @return An index template that could not be read, or of which none matched.
        public static IndexTemplate failed(final Status status, final String message) {
            return new IndexTemplate(status, null, null, null, message);
        }
    }

    /// A template that also matches the index name but has a lower priority, so does not apply.
    ///
    /// @param name          The template's name.
    /// @param indexPatterns The template's index patterns.
    /// @param priority      The template's priority, 0 if it sets none, as Elasticsearch treats it.
    @Schema(name = "ElasticOverlappingIndexTemplate")
    public record OverlappingTemplate(
            @JsonProperty("name") String name,
            @JsonProperty("index_patterns") List<String> indexPatterns,
            @JsonProperty("priority") long priority) {

    }

    /// A component template the index template is composed of.
    ///
    /// @param name    The component template's name, as the index template's `composed_of` gives it.
    /// @param status  Whether it was read.
    /// @param body    The component template as `GET _component_template` returns it, when read.
    /// @param message Why it could not be read, when it was not.
    @Schema(name = "ElasticComponentTemplate")
    @JsonInclude(Include.NON_NULL)
    public record ComponentTemplate(
            @JsonProperty("name") String name,
            @JsonProperty("status") Status status,
            @Schema(implementation = Object.class,
                    description = "The component template as GET _component_template returns it")
            @JsonRawValue @JsonProperty("body") String body,
            @JsonProperty("message") String message) {

    }
}
