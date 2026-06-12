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
import stroom.util.shared.validation.ValidRegex;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class InfoPopupConfig extends AbstractConfig implements IsStroomConfig {

    private static final String DEFAULT_QUERY_INFO_POPUP_TITLE = "Please Provide Query Info";
    private static final String DEFAULT_QUERY_INFO_VALIDATION_REGEX = "^[\\s\\S]{3,}$";

    @JsonProperty
    @JsonPropertyDescription("If you would like users to provide some query info when performing a query " +
            "set this property to true.")
    private final boolean enabled;

    @JsonProperty
    @JsonPropertyDescription("The title of the query info popup.")
    private final String title;

    @JsonProperty
    @JsonPropertyDescription("A regex used to validate query info.")
    @ValidRegex
    private final String validationRegex;

    public InfoPopupConfig() {
        enabled = false;
        title = DEFAULT_QUERY_INFO_POPUP_TITLE;
        validationRegex = DEFAULT_QUERY_INFO_VALIDATION_REGEX;
    }

    @JsonCreator
    public InfoPopupConfig(@JsonProperty("enabled") final boolean enabled,
                           @JsonProperty("title") final String title,
                           @JsonProperty("validationRegex") @ValidRegex final String validationRegex) {
        this.enabled = enabled;
        this.title = title;
        this.validationRegex = validationRegex;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTitle() {
        return title;
    }

    public String getValidationRegex() {
        return validationRegex;
    }

    @Override
    public String toString() {
        return "InfoPopupConfig{" +
                "enabled=" + enabled +
                ", title='" + title + '\'' +
                ", validationRegex='" + validationRegex + '\'' +
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
        final InfoPopupConfig that = (InfoPopupConfig) o;
        return enabled == that.enabled &&
                Objects.equals(title, that.title) &&
                Objects.equals(validationRegex, that.validationRegex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, title, validationRegex);
    }
}
