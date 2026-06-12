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
public class SplashConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("If you would like users to see a splash screen on login.")
    private final boolean enabled;

    @JsonProperty
    @JsonPropertyDescription("The title of the splash screen popup.")
    private final String title;

    @JsonProperty
    @JsonPropertyDescription("The HTML to display in the splash screen.")
    private final String body;

    @JsonProperty
    @JsonPropertyDescription("The version of the splash screen message.")
    private final String version;

    public SplashConfig() {
        enabled = false;
        title = "Splash Screen";
        body = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
        version = "v0.1";
    }

    @JsonCreator
    public SplashConfig(@JsonProperty("enabled") final boolean enabled,
                        @JsonProperty("title") final String title,
                        @JsonProperty("body") final String body,
                        @JsonProperty("version") final String version) {
        this.enabled = enabled;
        this.title = title;
        this.body = body;
        this.version = version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "SplashConfig{" +
                "enabled=" + enabled +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", version='" + version + '\'' +
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
        final SplashConfig that = (SplashConfig) o;
        return enabled == that.enabled &&
                Objects.equals(title, that.title) &&
                Objects.equals(body, that.body) &&
                Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, title, body, version);
    }
}
