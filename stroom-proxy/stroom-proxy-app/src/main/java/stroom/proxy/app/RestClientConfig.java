package stroom.proxy.app;

import stroom.util.shared.AbstractProxyConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.client.JerseyClientConfiguration;

import java.util.Objects;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class RestClientConfig extends JerseyClientConfiguration implements IsProxyConfig {

    // This class makes it easier for us to use/bind the JerseyClientConfiguration in our config tree
    // and makes it easier to find its usages in IJ

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    @JsonIgnore
    private PropertyPath basePropertyPath = PropertyPath.blank();

    /**
     * @return The base property path, e.g. "stroom.node" for this config object
     */
    @JsonIgnore
    public String getBasePath() {
        Objects.requireNonNull(basePropertyPath);
        return basePropertyPath.toString();
    }

    /**
     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
     * object
     */
    public String getFullPath(final String propertyName) {
        Objects.requireNonNull(basePropertyPath);
        Objects.requireNonNull(propertyName);
        return basePropertyPath.merge(propertyName).toString();
    }

    @JsonIgnore
    public void setBasePath(final PropertyPath basePropertyPath) {
        this.basePropertyPath = basePropertyPath;
    }
}
