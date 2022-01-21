package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface HasPropertyPath {

    @JsonIgnore
    PropertyPath getBasePath();

    /**
     * @return The base property path, e.g. "stroom.node" for this config object
     */
    @JsonIgnore
    String getBasePathStr();

    @JsonIgnore
    PropertyPath getFullPath(final String propertyName);

    /**
     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
     * object
     */
    @JsonIgnore
    String getFullPathStr(final String propertyName);


    @JsonIgnore
    void setBasePath(final PropertyPath basePropertyPath);
}

