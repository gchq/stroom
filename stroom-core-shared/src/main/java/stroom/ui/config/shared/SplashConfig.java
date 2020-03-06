package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({"enabled", "title", "body", "version"})
@JsonInclude(Include.NON_DEFAULT)
public class SplashConfig extends AbstractConfig {
    @JsonProperty
    @JsonPropertyDescription("If you would like users to see a splash screen on login.")
    private Boolean enabled;
    @JsonProperty
    @JsonPropertyDescription("The title of the splash screen popup.")
    private String title;
    @JsonProperty
    @JsonPropertyDescription("The HTML to display in the splash screen.")
    private String body;
    @JsonProperty
    @JsonPropertyDescription("The version of the splash screen message.")
    private String version;

    public SplashConfig() {
        setDefaults();
    }

    @JsonCreator
    public SplashConfig(@JsonProperty("enabled") final Boolean enabled,
                        @JsonProperty("title") final String title,
                        @JsonProperty("body") final String body,
                        @JsonProperty("version") final String version) {
        this.enabled = enabled;
        this.title = title;
        this.body = body;
        this.version = version;

        setDefaults();
    }

    private void setDefaults() {
        if (enabled == null) {
            enabled = false;
        }
        if (title == null) {
            title = "Splash Screen";
        }
        if (body == null) {
            body = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
        }
        if (version == null) {
            version = "v0.1";
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(final String body) {
        this.body = body;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }
}
