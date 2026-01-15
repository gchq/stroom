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
public class AnalyticUiDefaultConfig extends AbstractAnalyticUiDefaultConfig implements IsStroomConfig {


    @JsonProperty
    @JsonPropertyDescription("The default node to execute the analytic on.")
    private final String defaultNode;

    @JsonProperty
    @JsonPropertyDescription("The default feed to send analytic errors to.")
    private final DocRef defaultErrorFeed;

    @JsonProperty
    @JsonPropertyDescription("The default feed to send analytic results to.")
    private final DocRef defaultDestinationFeed;

    @JsonProperty
    @JsonPropertyDescription(
            "The default email subject template to use for analytic rule emails. The template uses " +
            "a sub-set of the Jinja templating language. If this property is not set, the user " +
            "will not be presented with an initial subject template value.")
    private final String defaultSubjectTemplate;

    @JsonProperty
    @JsonPropertyDescription(
            "The default email body template to use for analytic rule emails. The template uses " +
            "a sub-set of the Jinja templating language. If this property is not set, the user will " +
            "not be presented with an initial body template value.")
    private final String defaultBodyTemplate;

    @SuppressWarnings("checkstyle:LineLength")
    public AnalyticUiDefaultConfig() {
        defaultNode = null;
        defaultErrorFeed = null;
        defaultDestinationFeed = null;
        defaultSubjectTemplate = "Detector '{{ detectorName | escape }}' Alert";
        //noinspection TextBlockMigration // GWT no likey textblock, grrr!
        defaultBodyTemplate =
                "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<meta charset=\"UTF-8\" />\n" +
                "<title>Detector '{{ detectorName | escape }}' Alert</title>\n" +
                "<body>\n" +
                "  <p>Detector <em>{{ detectorName | escape }}</em> " +
                "{{ detectorVersion | escape }} fired at {{ detectTime | escape }}</p>\n" +
                "\n" +
                "  {%- if (values | length) > 0 -%}\n" +
                "  <p>Detail: {{ headline | escape }}</p>\n" +
                "  <ul>\n" +
                "    {% for key, val in values | dictsort -%}\n" +
                "      <li><strong>{{ key | escape }}</strong>: {{ val | escape }}</li>\n" +
                "    {% endfor %}\n" +
                "  </ul>\n" +
                "  {% endif -%}\n" +
                "\n" +
                "  {%- if (linkedEvents | length) > 0 -%}\n" +
                "  <p>Linked Events:</p>\n" +
                "  <ul>\n" +
                "    {% for linkedEvent in linkedEvents -%}\n" +
                "      <li>Environment: {{ linkedEvent.stroom | escape }}, " +
                "Stream ID: {{ linkedEvent.streamId | escape }}, " +
                "Event ID: {{ linkedEvent.eventId | escape }}</li>\n" +
                "    {% endfor %}\n" +
                "  </ul>\n" +
                "  {% endif %}\n" +
                "</body>\n";
    }

    @JsonCreator
    public AnalyticUiDefaultConfig(
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
        return "AnalyticUiDefaultConfig{" +
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
        final AnalyticUiDefaultConfig that = (AnalyticUiDefaultConfig) object;
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
