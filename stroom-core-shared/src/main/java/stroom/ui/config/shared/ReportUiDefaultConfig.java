/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.ui.config.shared;

import stroom.docref.DocRef;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ReportUiDefaultConfig  extends AbstractAnalyticUiDefaultConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("The default node to execute the report on.")
    private final String defaultNode;

    @JsonProperty
    @JsonPropertyDescription("The default feed to send report errors to.")
    private final DocRef defaultErrorFeed;

    @JsonProperty
    @JsonPropertyDescription("The default feed to send report results to.")
    private final DocRef defaultDestinationFeed;

    @JsonProperty
    @JsonPropertyDescription(
            "The default email subject template to use for report emails. The template uses " +
            "a sub-set of the Jinja templating language. If this property is not set, the user " +
            "will not be presented with an initial subject template value.")
    private final String defaultSubjectTemplate;

    @JsonProperty
    @JsonPropertyDescription(
            "The default email body template to use for report emails. The template uses " +
            "a sub-set of the Jinja templating language. If this property is not set, the user will " +
            "not be presented with an initial body template value.")
    private final String defaultBodyTemplate;

    @SuppressWarnings("checkstyle:LineLength")
    public ReportUiDefaultConfig() {
        defaultNode = null;
        defaultErrorFeed = null;
        defaultDestinationFeed = null;
        defaultSubjectTemplate = "Report '{{ reportName | escape }}'";
        //noinspection TextBlockMigration // GWT no likey textblock, grrr!
        defaultBodyTemplate =
                "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<meta charset=\"UTF-8\" />\n" +
                "<title>Report '{{ reportName | escape }}'</title>\n" +
                "<body>\n" +
                " <p><em>Report: {{ reportName | escape }}</em> " +
                " executed for {{ effectiveExecutionTime | escape }} on {{ executionTime | escape }}</p>\n" +
                " <p><em>Description:</em> " +
                " {{ description | escape }}</p>\n" +
                "</body>\n";
    }

    @JsonCreator
    public ReportUiDefaultConfig(
            @JsonProperty("defaultNode") final String defaultNode,
            @JsonProperty("defaultErrorFeed") final DocRef defaultErrorFeed,
            @JsonProperty("defaultDestinationFeed") final DocRef defaultDestinationFeed,
            @JsonProperty("defaultSubjectTemplate") final String defaultSubjectTemplate,
            @JsonProperty("defaultBodyTemplate") final String defaultBodyTemplate) {
        this.defaultNode = defaultNode;
        this.defaultErrorFeed = defaultErrorFeed;
        this.defaultDestinationFeed = defaultDestinationFeed;
        this.defaultSubjectTemplate = defaultSubjectTemplate;
        this.defaultBodyTemplate = defaultBodyTemplate;
    }

    @Override
    public String getDefaultNode() {
        return defaultNode;
    }

    @Override
    public DocRef getDefaultErrorFeed() {
        return defaultErrorFeed;
    }

    @Override
    public DocRef getDefaultDestinationFeed() {
        return defaultDestinationFeed;
    }

    @Override
    public String getDefaultSubjectTemplate() {
        return defaultSubjectTemplate;
    }

    @Override
    public String getDefaultBodyTemplate() {
        return defaultBodyTemplate;
    }

    @Override
    public String toString() {
        return "ReportUiDefaultConfig{" +
               "defaultNode='" + defaultNode + '\'' +
               ", defaultErrorFeed=" + defaultErrorFeed +
               ", defaultDestinationFeed=" + defaultDestinationFeed +
               ", defaultSubjectTemplate='" + defaultSubjectTemplate + '\'' +
               ", defaultBodyTemplate='" + defaultBodyTemplate + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final ReportUiDefaultConfig that = (ReportUiDefaultConfig) object;
        return Objects.equals(defaultNode, that.defaultNode)
               && Objects.equals(defaultErrorFeed, that.defaultErrorFeed)
               && Objects.equals(defaultDestinationFeed, that.defaultDestinationFeed)
               && Objects.equals(defaultSubjectTemplate, that.defaultSubjectTemplate)
               && Objects.equals(defaultBodyTemplate, that.defaultBodyTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultNode,
                defaultErrorFeed,
                defaultDestinationFeed,
                defaultSubjectTemplate,
                defaultBodyTemplate);
    }
}
