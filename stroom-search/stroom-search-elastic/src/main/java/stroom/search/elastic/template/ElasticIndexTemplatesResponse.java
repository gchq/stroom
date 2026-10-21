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

import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

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
/// The resolved name is written as null rather than left out, so it is always there to read.
@JsonInclude(Include.ALWAYS)
public class ElasticIndexTemplatesResponse {

    @JsonProperty
    private final String indexName;
    @JsonProperty
    private final String resolvedName;
    @JsonInclude(Include.NON_NULL)
    @JsonProperty
    private final String resolveMessage;
    @JsonProperty
    private final IndexTemplate indexTemplate;
    @JsonProperty
    private final List<ComponentTemplate> componentTemplates;

    /// @param indexName          The doc's index name, which may be a pattern or an alias.
    /// @param resolvedName       The newest concrete index or data stream the index name stands for, or null
    ///                           if nothing resolved or resolving was refused.
    /// @param resolveMessage     Why the index name could not be resolved, if resolving failed.
    /// @param indexTemplate      The index template that applies to the resolved name.
    /// @param componentTemplates The component templates the index template is composed of, in its order.
    ///                           Empty unless the index template was read.
    @JsonCreator
    public ElasticIndexTemplatesResponse(@JsonProperty("indexName") final String indexName,
                                         @JsonProperty("resolvedName") final String resolvedName,
                                         @JsonProperty("resolveMessage") final String resolveMessage,
                                         @JsonProperty("indexTemplate") final IndexTemplate indexTemplate,
                                         @JsonProperty("componentTemplates")
                                         final List<ComponentTemplate> componentTemplates) {
        this.indexName = indexName;
        this.resolvedName = resolvedName;
        this.resolveMessage = resolveMessage;
        this.indexTemplate = indexTemplate;
        this.componentTemplates = componentTemplates;
    }

    /// @return The doc's index name, which may be a pattern or an alias.
    public String getIndexName() {
        return indexName;
    }

    /// @return The newest concrete index or data stream the index name stands for, or null.
    public String getResolvedName() {
        return resolvedName;
    }

    /// @return Why the index name could not be resolved, if resolving failed.
    public String getResolveMessage() {
        return resolveMessage;
    }

    /// @return The index template that applies to the resolved name.
    public IndexTemplate getIndexTemplate() {
        return indexTemplate;
    }

    /// @return The component templates the index template is composed of, in its order.
    public List<ComponentTemplate> getComponentTemplates() {
        return componentTemplates;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElasticIndexTemplatesResponse that = (ElasticIndexTemplatesResponse) o;
        return Objects.equals(indexName, that.indexName)
               && Objects.equals(resolvedName, that.resolvedName)
               && Objects.equals(resolveMessage, that.resolveMessage)
               && Objects.equals(indexTemplate, that.indexTemplate)
               && Objects.equals(componentTemplates, that.componentTemplates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(indexName, resolvedName, resolveMessage, indexTemplate, componentTemplates);
    }

    @Override
    public String toString() {
        return "ElasticIndexTemplatesResponse{" +
               "indexName='" + indexName + '\'' +
               ", resolvedName='" + resolvedName + '\'' +
               ", resolveMessage='" + resolveMessage + '\'' +
               ", indexTemplate=" + indexTemplate +
               ", componentTemplates=" + componentTemplates +
               '}';
    }

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
    @Schema(name = "ElasticIndexTemplate")
    @JsonInclude(Include.NON_NULL)
    public static class IndexTemplate {

        @JsonProperty
        private final Status status;
        @JsonProperty
        private final String name;
        @Schema(implementation = Object.class, description = "The template as GET _index_template returns it")
        @JsonRawValue
        @JsonProperty
        private final String body;
        @JsonProperty
        private final List<OverlappingTemplate> overlapping;
        @JsonProperty
        private final String message;

        /// @param status      Whether it was read.
        /// @param name        The template's name, when one matched.
        /// @param body        The template as `GET _index_template` returns it, when one matched.
        /// @param overlapping The other matching templates, highest priority first, which do not apply.
        /// @param message     Why it could not be read, when it was not.
        @JsonCreator
        public IndexTemplate(@JsonProperty("status") final Status status,
                             @JsonProperty("name") final String name,
                             @JsonProperty("body") final String body,
                             @JsonProperty("overlapping") final List<OverlappingTemplate> overlapping,
                             @JsonProperty("message") final String message) {
            this.status = status;
            this.name = name;
            this.body = body;
            this.overlapping = overlapping;
            this.message = message;
        }

        // The body is raw JSON, which can't be any value the serialisation test gives a string.
        @SerialisationTestConstructor
        private IndexTemplate() {
            this(Status.OK, "test", null, List.of(new OverlappingTemplate("test", List.of("test-*"), 1L)), null);
        }

        /// @param status  Why it wasn't read: not found, forbidden or an error.
        /// @param message Why it could not be read.
        /// @return An index template that could not be read, or of which none matched.
        public static IndexTemplate failed(final Status status, final String message) {
            return new IndexTemplate(status, null, null, null, message);
        }

        /// @return Whether it was read.
        public Status getStatus() {
            return status;
        }

        /// @return The template's name, when one matched.
        public String getName() {
            return name;
        }

        /// @return The template as `GET _index_template` returns it, when one matched.
        public String getBody() {
            return body;
        }

        /// @return The other matching templates, highest priority first, which do not apply.
        public List<OverlappingTemplate> getOverlapping() {
            return overlapping;
        }

        /// @return Why it could not be read, when it was not.
        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final IndexTemplate that = (IndexTemplate) o;
            return status == that.status
                   && Objects.equals(name, that.name)
                   && Objects.equals(body, that.body)
                   && Objects.equals(overlapping, that.overlapping)
                   && Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, name, body, overlapping, message);
        }

        @Override
        public String toString() {
            return "IndexTemplate{" +
                   "status=" + status +
                   ", name='" + name + '\'' +
                   ", body='" + body + '\'' +
                   ", overlapping=" + overlapping +
                   ", message='" + message + '\'' +
                   '}';
        }
    }

    /// A template that also matches the index name but has a lower priority, so does not apply.
    @Schema(name = "ElasticOverlappingIndexTemplate")
    @JsonInclude(Include.ALWAYS)
    public static class OverlappingTemplate {

        @JsonProperty
        private final String name;
        @JsonProperty("index_patterns")
        private final List<String> indexPatterns;
        @JsonProperty
        private final long priority;

        /// @param name          The template's name.
        /// @param indexPatterns The template's index patterns.
        /// @param priority      The template's priority, 0 if it sets none, as Elasticsearch treats it.
        @JsonCreator
        public OverlappingTemplate(@JsonProperty("name") final String name,
                                   @JsonProperty("index_patterns") final List<String> indexPatterns,
                                   @JsonProperty("priority") final Long priority) {
            this.name = name;
            this.indexPatterns = indexPatterns;
            this.priority = Objects.requireNonNullElse(priority, 0L);
        }

        /// @return The template's name.
        public String getName() {
            return name;
        }

        /// @return The template's index patterns.
        public List<String> getIndexPatterns() {
            return indexPatterns;
        }

        /// @return The template's priority, 0 if it sets none.
        public long getPriority() {
            return priority;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final OverlappingTemplate that = (OverlappingTemplate) o;
            return priority == that.priority
                   && Objects.equals(name, that.name)
                   && Objects.equals(indexPatterns, that.indexPatterns);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, indexPatterns, priority);
        }

        @Override
        public String toString() {
            return "OverlappingTemplate{" +
                   "name='" + name + '\'' +
                   ", indexPatterns=" + indexPatterns +
                   ", priority=" + priority +
                   '}';
        }
    }

    /// A component template the index template is composed of.
    @Schema(name = "ElasticComponentTemplate")
    @JsonInclude(Include.NON_NULL)
    public static class ComponentTemplate {

        @JsonProperty
        private final String name;
        @JsonProperty
        private final Status status;
        @Schema(implementation = Object.class,
                description = "The component template as GET _component_template returns it")
        @JsonRawValue
        @JsonProperty
        private final String body;
        @JsonProperty
        private final String message;

        /// @param name    The component template's name, as the index template's `composed_of` gives it.
        /// @param status  Whether it was read.
        /// @param body    The component template as `GET _component_template` returns it, when read.
        /// @param message Why it could not be read, when it was not.
        @JsonCreator
        public ComponentTemplate(@JsonProperty("name") final String name,
                                 @JsonProperty("status") final Status status,
                                 @JsonProperty("body") final String body,
                                 @JsonProperty("message") final String message) {
            this.name = name;
            this.status = status;
            this.body = body;
            this.message = message;
        }

        // The body is raw JSON, which can't be any value the serialisation test gives a string.
        @SerialisationTestConstructor
        private ComponentTemplate() {
            this("test", Status.OK, null, null);
        }

        /// @return The component template's name.
        public String getName() {
            return name;
        }

        /// @return Whether it was read.
        public Status getStatus() {
            return status;
        }

        /// @return The component template as `GET _component_template` returns it, when read.
        public String getBody() {
            return body;
        }

        /// @return Why it could not be read, when it was not.
        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ComponentTemplate that = (ComponentTemplate) o;
            return Objects.equals(name, that.name)
                   && status == that.status
                   && Objects.equals(body, that.body)
                   && Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, status, body, message);
        }

        @Override
        public String toString() {
            return "ComponentTemplate{" +
                   "name='" + name + '\'' +
                   ", status=" + status +
                   ", body='" + body + '\'' +
                   ", message='" + message + '\'' +
                   '}';
        }
    }
}
