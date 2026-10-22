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

package stroom.ai.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// One pipeline stage (e.g. translation: Raw Events to Events) as the agent's tools find and read pipelines: where
/// the templates for a new pipeline of the stage are looked for, and what marks a pipeline as the stage when its
/// elements don't say (its schema group or the stream type it writes). Each stage's defaults are given by
/// [AiPipelineStagesConfig]; a property left out keeps its stage's default.
@JsonPropertyOrder(alphabetic = true)
public class AiPipelineStageConfig extends AbstractConfig implements IsStroomConfig {

    private final List<String> templateFolders;
    private final List<String> templateNames;
    private final List<String> schemaGroups;
    private final List<String> streamTypes;

    /// A stage with no template folders and no markers.
    public AiPipelineStageConfig() {
        this(List.of(), List.of(), List.of(), List.of());
    }

    /// @param templateFolders Explorer paths, from System, where templates are looked for, or null for the stage's
    ///                        default.
    /// @param templateNames   Glob patterns on template names, or null for the stage's default.
    /// @param schemaGroups    SchemaFilter schema groups that mark a pipeline as the stage, or null for the stage's
    ///                        default.
    /// @param streamTypes     StreamAppender stream types that mark a pipeline as the stage, or null for the
    ///                        stage's default.
    @JsonCreator
    public AiPipelineStageConfig(@JsonProperty("templateFolders") final List<String> templateFolders,
                                 @JsonProperty("templateNames") final List<String> templateNames,
                                 @JsonProperty("schemaGroups") final List<String> schemaGroups,
                                 @JsonProperty("streamTypes") final List<String> streamTypes) {
        this.templateFolders = copyOf(templateFolders);
        this.templateNames = copyOf(templateNames);
        this.schemaGroups = copyOf(schemaGroups);
        this.streamTypes = copyOf(streamTypes);
    }

    /// @param defaults The stage's defaults.
    /// @return This stage with each property it leaves out (null) taken from the defaults.
    AiPipelineStageConfig withDefaults(final AiPipelineStageConfig defaults) {
        return new AiPipelineStageConfig(
                Objects.requireNonNullElse(templateFolders, defaults.templateFolders),
                Objects.requireNonNullElse(templateNames, defaults.templateNames),
                Objects.requireNonNullElse(schemaGroups, defaults.schemaGroups),
                Objects.requireNonNullElse(streamTypes, defaults.streamTypes));
    }

    /// @return The Explorer paths, from System, where templates for the stage are looked for.
    @JsonPropertyDescription("The Explorer folders (paths from System, e.g. 'System/Template Pipelines') where the "
                             + "AI tools look for template pipelines for a new pipeline of this stage. Pipelines "
                             + "that other pipelines inherit from are candidates too, and Stroom's standard "
                             + "templates are the last resort.")
    @JsonProperty("templateFolders")
    public List<String> getTemplateFolders() {
        return templateFolders;
    }

    /// @return The glob patterns a template's name must match, if any are given.
    @JsonPropertyDescription("Glob patterns (e.g. 'Event Data (*)') that the names of the templates in "
                             + "templateFolders must match. Empty for any name.")
    @JsonProperty("templateNames")
    public List<String> getTemplateNames() {
        return templateNames;
    }

    /// @return The SchemaFilter schema groups that mark a pipeline as the stage.
    @JsonPropertyDescription("The SchemaFilter schemaGroup values that mark a pipeline as this stage, when its "
                             + "elements don't say. Change them when the environment's templates validate "
                             + "against other schema groups.")
    @JsonProperty("schemaGroups")
    public List<String> getSchemaGroups() {
        return schemaGroups;
    }

    /// @return The StreamAppender stream types that mark a pipeline as the stage.
    @JsonPropertyDescription("The StreamAppender streamType values that mark a pipeline as this stage, when its "
                             + "elements don't say. Change them when the environment's templates write other "
                             + "stream types.")
    @JsonProperty("streamTypes")
    public List<String> getStreamTypes() {
        return streamTypes;
    }

    private static List<String> copyOf(final List<String> values) {
        return values == null
                ? null
                : values.stream()
                        .filter(Objects::nonNull)
                        .toList();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final AiPipelineStageConfig that)) {
            return false;
        }
        return Objects.equals(templateFolders, that.templateFolders)
               && Objects.equals(templateNames, that.templateNames)
               && Objects.equals(schemaGroups, that.schemaGroups)
               && Objects.equals(streamTypes, that.streamTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(templateFolders, templateNames, schemaGroups, streamTypes);
    }

    @Override
    public String toString() {
        return "AiPipelineStageConfig{" +
               "templateFolders=" + templateFolders +
               ", templateNames=" + templateNames +
               ", schemaGroups=" + schemaGroups +
               ", streamTypes=" + streamTypes +
               '}';
    }
}
