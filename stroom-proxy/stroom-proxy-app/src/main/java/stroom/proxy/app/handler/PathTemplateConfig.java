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

package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;


@NotInjectableConfig // Used by multiple other config classes
@JsonPropertyOrder(alphabetic = true)
public class PathTemplateConfig extends AbstractConfig implements IsProxyConfig {

    public static final TemplatingMode DEFAULT_TEMPLATING_MODE = TemplatingMode.REPLACE_UNKNOWN_PARAMS;
    public static final String DATE_AND_FEED_TEMPLATE = "${year}${month}${day}/${feed}";
    public static final PathTemplateConfig DEFAULT = new PathTemplateConfig(
            true, DATE_AND_FEED_TEMPLATE, DEFAULT_TEMPLATING_MODE);
    public static final PathTemplateConfig DISABLED = new PathTemplateConfig(
            false, DATE_AND_FEED_TEMPLATE, DEFAULT_TEMPLATING_MODE);

    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final String pathTemplate;
    @JsonProperty
    private final TemplatingMode templatingMode;

    public PathTemplateConfig(final String pathTemplate) {
        this(true, pathTemplate, DEFAULT_TEMPLATING_MODE);
    }

    public PathTemplateConfig(final String pathTemplate,
                              final TemplatingMode templatingMode) {
        this(true, pathTemplate, templatingMode);
    }

    public PathTemplateConfig(@JsonProperty("enabled") final boolean enabled,
                              @JsonProperty("pathTemplate") final String pathTemplate,
                              @JsonProperty("templatingMode") final TemplatingMode templatingMode) {
        this.enabled = enabled;
        this.templatingMode = Objects.requireNonNullElse(templatingMode, DEFAULT_TEMPLATING_MODE);
        this.pathTemplate = Objects.requireNonNullElse(pathTemplate, DATE_AND_FEED_TEMPLATE);
    }

    @Pattern(regexp = "^[^/].*$") // Relative paths only
    @JsonPropertyDescription("A relative path that is optionally templated with parameters, e.g. " +
                             "'${year}${month}${day}/${feed}'. If this value is null, a default template " +
                             "will be used. To not use a path template, set enabled to false. " +
                             "Supported template params (must be lower-case) are: " +
                             "'${feed}', '${type}', '${year}', '${month}', '${day}', '${hour}', '${minute}'," +
                             "'${second}', '${millis}' and '${ms}'.")
    public String getPathTemplate() {
        return pathTemplate;
    }

    @JsonPropertyDescription("templatingMode controls how unknown template parameters are handled; " +
                             "'IGNORE_UNKNOWN_PARAMS' means unknown parameters are left exactly as they are, " +
                             "'REMOVE_UNKNOWN_PARAMS' means unknown parameters are replaced with nothing, " +
                             "i.e. removed," +
                             "'REPLACE_UNKNOWN_PARAMS' means unknown parameters are replaced with 'XXX'.")
    public TemplatingMode getTemplatingMode() {
        return templatingMode;
    }

    @JsonPropertyDescription("If false, the values of pathTemplate and templatingMode will be ignored and " +
                             "no path template (static or templated) will be used.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonIgnore
    boolean hasPathTemplate() {
        return enabled && NullSafe.isNonBlankString(pathTemplate);
    }

    @Override
    public String toString() {
        return "PathTemplateConfig{" +
               "pathTemplate='" + pathTemplate + '\'' +
               ", templatingMode=" + templatingMode +
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
        final PathTemplateConfig that = (PathTemplateConfig) o;
        return Objects.equals(pathTemplate, that.pathTemplate) && templatingMode == that.templatingMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathTemplate, templatingMode);
    }

    @ValidationMethod(message = "If templatingMode is not DISABLED, pathTemplate must be a non-blank sting.")
    boolean isPathTemplateValid() {
        return !enabled
               || NullSafe.isNonEmptyString(pathTemplate);
    }
}
