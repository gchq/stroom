package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface HasPropertyPath {

    /**
     * @return The base property path, e.g. "stroom.node" for this config object
     */
    @JsonIgnore
    String getBasePath();

    /**
     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
     * object
     */

    String getFullPath(final String propertyName);

    @JsonIgnore
    void setBasePath(final PropertyPath basePropertyPath);
}

