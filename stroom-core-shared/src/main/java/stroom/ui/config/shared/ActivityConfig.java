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

import stroom.util.shared.AbstractConfig;
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
public class ActivityConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("If you would like users to be able to record some info about the activity they " +
            "are performing set this property to true.")
    private final boolean enabled;

    @JsonProperty
    @JsonPropertyDescription("Set to true if users should be prompted to choose an activity on login.")
    private final boolean chooseOnStartup;

    @JsonProperty
    @JsonPropertyDescription("The title of the activity manager popup.")
    private final String managerTitle;

    @JsonProperty
    @JsonPropertyDescription("The title of the activity editor popup.")
    private final String editorTitle;

    @JsonProperty
    @JsonPropertyDescription("The HTML to display in the activity editor popup.")
    private final String editorBody;

    public ActivityConfig() {
        enabled = false;
        chooseOnStartup = false;
        managerTitle = "Choose Activity";
        editorTitle = "Edit Activity";
        editorBody = "Activity Code:</br>" +
                "<input type=\"text\" name=\"code\"></input></br></br>" +
                "Activity Description:</br>" +
                "<textarea " +
                "rows=\"4\" " +
                "style=\"width:100%;height:80px\" " +
                "name=\"description\" " +
                "validation=\".{80,}\" " +
                "validationMessage=\"The activity description must be at least 80 characters long.\" >" +
                "</textarea>" +
                "Explain what the activity is";
    }

    @JsonCreator
    public ActivityConfig(@JsonProperty("enabled") final boolean enabled,
                          @JsonProperty("chooseOnStartup") final boolean chooseOnStartup,
                          @JsonProperty("managerTitle") final String managerTitle,
                          @JsonProperty("editorTitle") final String editorTitle,
                          @JsonProperty("editorBody") final String editorBody) {
        this.enabled = enabled;
        this.chooseOnStartup = chooseOnStartup;
        this.managerTitle = managerTitle;
        this.editorTitle = editorTitle;
        this.editorBody = editorBody;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isChooseOnStartup() {
        return chooseOnStartup;
    }

    public String getManagerTitle() {
        return managerTitle;
    }

    public String getEditorTitle() {
        return editorTitle;
    }

    public String getEditorBody() {
        return editorBody;
    }

    @Override
    public String toString() {
        return "ActivityConfig{" +
                "enabled=" + enabled +
                ", chooseOnStartup=" + chooseOnStartup +
                ", managerTitle='" + managerTitle + '\'' +
                ", editorTitle='" + editorTitle + '\'' +
                ", editorBody='" + editorBody + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ActivityConfig that = (ActivityConfig) o;
        return enabled == that.enabled &&
                chooseOnStartup == that.chooseOnStartup &&
                Objects.equals(managerTitle, that.managerTitle) &&
                Objects.equals(editorTitle, that.editorTitle) &&
                Objects.equals(editorBody, that.editorBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, chooseOnStartup, managerTitle, editorTitle, editorBody);
    }
}
