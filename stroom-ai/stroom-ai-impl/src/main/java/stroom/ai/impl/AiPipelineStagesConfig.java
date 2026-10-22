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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The pipeline stages as the agent's tools find and read pipelines (e.g. find_pipeline_templates): for each, where
/// its templates are looked for and what marks a pipeline as it. The defaults are Stroom's standard template
/// folders and names, and its standard schema groups and stream types.
@JsonPropertyOrder(alphabetic = true)
public class AiPipelineStagesConfig extends AbstractConfig implements IsStroomConfig {

    /// The folder Stroom's standard templates are in.
    public static final String TEMPLATE_PIPELINES = "System/Template Pipelines";

    static final AiPipelineStageConfig DEFAULT_TRANSLATION = new AiPipelineStageConfig(
            List.of(TEMPLATE_PIPELINES), List.of("Event Data (*)"), List.of("EVENTS"), List.of("Events"));
    static final AiPipelineStageConfig DEFAULT_INDEXING = new AiPipelineStageConfig(
            List.of(TEMPLATE_PIPELINES, TEMPLATE_PIPELINES + "/Elasticsearch"),
            List.of("Indexing", "Events to Elasticsearch", "Indexing - Elasticsearch"), List.of(), List.of());
    static final AiPipelineStageConfig DEFAULT_DISCOVERY = new AiPipelineStageConfig(
            List.of(TEMPLATE_PIPELINES + "/Elasticsearch"), List.of("Raw to Elasticsearch"), List.of(), List.of());
    static final AiPipelineStageConfig DEFAULT_REFERENCE = new AiPipelineStageConfig(
            List.of(TEMPLATE_PIPELINES), List.of("Reference Data"), List.of("REFERENCE_DATA"), List.of("Reference"));
    // A pipeline that writes records:2 records as a stream of their own (e.g. a source's parsed text).
    static final AiPipelineStageConfig DEFAULT_RECORDS = new AiPipelineStageConfig(
            List.of(), List.of(), List.of("RECORDS"), List.of("Records"));
    static final AiPipelineStageConfig DEFAULT_FORWARDING = new AiPipelineStageConfig(
            List.of(), List.of(), List.of(), List.of());

    private final AiPipelineStageConfig translation;
    private final AiPipelineStageConfig indexing;
    private final AiPipelineStageConfig discovery;
    private final AiPipelineStageConfig reference;
    private final AiPipelineStageConfig records;
    private final AiPipelineStageConfig forwarding;

    /// The default stages.
    public AiPipelineStagesConfig() {
        this(null, null, null, null, null, null);
    }

    /// @param translation The translation stage (Raw Events to Events), or null for its defaults.
    /// @param indexing    The indexing stage (Events to an index), or null for its defaults.
    /// @param discovery   The discovery stage (raw structured data straight to an index), or null for its
    ///                    defaults.
    /// @param reference   The reference stage (a Raw Reference feed to reference data maps), or null for its
    ///                    defaults.
    /// @param records     The records stage (a source to a Records stream), or null for its defaults.
    /// @param forwarding  The forwarding stage (Events sent on, e.g. to Kafka), or null for its defaults.
    @JsonCreator
    public AiPipelineStagesConfig(@JsonProperty("translation") final AiPipelineStageConfig translation,
                                  @JsonProperty("indexing") final AiPipelineStageConfig indexing,
                                  @JsonProperty("discovery") final AiPipelineStageConfig discovery,
                                  @JsonProperty("reference") final AiPipelineStageConfig reference,
                                  @JsonProperty("records") final AiPipelineStageConfig records,
                                  @JsonProperty("forwarding") final AiPipelineStageConfig forwarding) {
        this.translation = withDefaults(translation, DEFAULT_TRANSLATION);
        this.indexing = withDefaults(indexing, DEFAULT_INDEXING);
        this.discovery = withDefaults(discovery, DEFAULT_DISCOVERY);
        this.reference = withDefaults(reference, DEFAULT_REFERENCE);
        this.records = withDefaults(records, DEFAULT_RECORDS);
        this.forwarding = withDefaults(forwarding, DEFAULT_FORWARDING);
    }

    /// @return The translation stage: Raw Events to Events, always the first pipeline for a new source.
    @JsonPropertyDescription("The translation stage (Raw Events to Events).")
    @JsonProperty("translation")
    public AiPipelineStageConfig getTranslation() {
        return translation;
    }

    /// @return The indexing stage: Events to an index.
    @JsonPropertyDescription("The indexing stage (Events to an index).")
    @JsonProperty("indexing")
    public AiPipelineStageConfig getIndexing() {
        return indexing;
    }

    /// @return The discovery stage: raw structured data straight to an index.
    @JsonPropertyDescription("The discovery stage (raw structured data straight to an index).")
    @JsonProperty("discovery")
    public AiPipelineStageConfig getDiscovery() {
        return discovery;
    }

    /// @return The reference stage: a Raw Reference feed to the reference data maps lookups read.
    @JsonPropertyDescription("The reference stage (a Raw Reference feed to the reference data maps that "
                             + "stroom:lookup() reads).")
    @JsonProperty("reference")
    public AiPipelineStageConfig getReference() {
        return reference;
    }

    /// @return The records stage: a source to a Records stream of records:2 records.
    @JsonPropertyDescription("The records stage (a source to a Records stream of records:2 records).")
    @JsonProperty("records")
    public AiPipelineStageConfig getRecords() {
        return records;
    }

    /// @return The forwarding stage: Events sent on, e.g. to Kafka.
    @JsonPropertyDescription("The forwarding stage (Events sent on, e.g. to Kafka as CEF for ArcSight).")
    @JsonProperty("forwarding")
    public AiPipelineStageConfig getForwarding() {
        return forwarding;
    }

    /// @return Each stage by its name (translation, reference, records, indexing, discovery, forwarding), in the
    /// order a pipeline's markers are tested against them.
    @JsonIgnore
    public Map<String, AiPipelineStageConfig> byName() {
        final Map<String, AiPipelineStageConfig> stages = new LinkedHashMap<>();
        stages.put("translation", translation);
        stages.put("reference", reference);
        stages.put("records", records);
        stages.put("indexing", indexing);
        stages.put("discovery", discovery);
        stages.put("forwarding", forwarding);
        return stages;
    }

    private static AiPipelineStageConfig withDefaults(final AiPipelineStageConfig given,
                                                      final AiPipelineStageConfig defaults) {
        return given == null
                ? defaults
                : given.withDefaults(defaults);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final AiPipelineStagesConfig that)) {
            return false;
        }
        return Objects.equals(translation, that.translation)
               && Objects.equals(indexing, that.indexing)
               && Objects.equals(discovery, that.discovery)
               && Objects.equals(reference, that.reference)
               && Objects.equals(records, that.records)
               && Objects.equals(forwarding, that.forwarding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(translation, indexing, discovery, reference, records, forwarding);
    }

    @Override
    public String toString() {
        return "AiPipelineStagesConfig{" +
               "translation=" + translation +
               ", indexing=" + indexing +
               ", discovery=" + discovery +
               ", reference=" + reference +
               ", records=" + records +
               ", forwarding=" + forwarding +
               '}';
    }
}
