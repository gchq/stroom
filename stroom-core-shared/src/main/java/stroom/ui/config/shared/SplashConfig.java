package stroom.ui.config.shared;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.docref.SharedObject;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class SplashConfig extends IsConfig implements SharedObject {
    private boolean enabled;
    private String title = "Splash Screen";
    private String body = "<h1>About Stroom</h1><p>Stroom is designed to receive data from multiple systems.</p>";
    private String version = "v0.1";

    public SplashConfig() {
        // Default constructor necessary for GWT serialisation.
    }

    @JsonPropertyDescription("If you would like users to see a splash screen on login.")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("The title of the splash screen popup.")
    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    @JsonPropertyDescription("The HTML to display in the splash screen.")
    public String getBody() {
        return body;
    }

    public void setBody(final String body) {
        this.body = body;
    }

    @JsonPropertyDescription("The version of the splash screen message.")
    public String getVersion() {
        return version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }
}
