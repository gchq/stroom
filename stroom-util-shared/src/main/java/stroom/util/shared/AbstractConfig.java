package stroom.util.shared;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Marker interface for java bean classes used to provide configuration properties.
 * This includes AppConfig and the classes that sit beneath it.  Implementing classes
 * are expected to be (de)serialised from/to YAML configuration files.
 */
public abstract class AbstractConfig {

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    private String[] basePathParts = new String[] {};

    /**
     * @return The base property path, e.g. "stroom.node" for this config object
     */
    public String getBasePath() {
        Objects.requireNonNull(basePathParts);
        return joinBaseParts().toString();
    }

    /**
     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
     * object
     */
    public String getFullPath(final String propertyName) {
        Objects.requireNonNull(basePathParts);
        Objects.requireNonNull(propertyName);
        return joinBaseParts()
            .add(propertyName)
            .toString();
    }

    private StringJoiner joinBaseParts() {
        StringJoiner stringJoiner = new StringJoiner(".");
        for (final String basePathPart : basePathParts) {
            stringJoiner.add(basePathPart);
        }
        return stringJoiner;
    }

    public void setBasePath(final String basePath) {
        this.basePathParts = basePath.split("\\.");
    }
}
