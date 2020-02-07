package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;


import java.util.Objects;

/**
 * Marker interface for java bean classes used to provide configuration properties.
 * This includes AppConfig and the classes that sit beneath it.  Implementing classes
 * are expected to be (de)serialised from/to YAML configuration files.
 */
public abstract class AbstractConfig {

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
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
    @JsonIgnore
    public String getFullPath(final String propertyName) {
        Objects.requireNonNull(basePropertyPath);
        Objects.requireNonNull(propertyName);
        return basePropertyPath.merge(propertyName).toString();
    }

    public void setBasePath(final PropertyPath basePropertyPath) {
        this.basePropertyPath = basePropertyPath;
    }
}
