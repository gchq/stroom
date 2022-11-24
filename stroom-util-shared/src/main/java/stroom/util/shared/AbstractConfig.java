package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Super class for all stroom config pojos. Can be decorated with property path
 * information, e.g. stroom.path.home
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public abstract class AbstractConfig implements HasPropertyPath {

    // Held in part form to reduce memory overhead as some parts will be used
    // many times over all the config objects
    @JsonIgnore
    private PropertyPath basePropertyPath = PropertyPath.blank();

    @Override
    @JsonIgnore
    public PropertyPath getBasePath() {
        return basePropertyPath;
    }

    /**
     * @return The base property path, e.g. "stroom.node" for this config object
     */
    @Override
    @JsonIgnore
    public String getBasePathStr() {
        return getBasePath().toString();
    }

    @Override
    @JsonIgnore
    public PropertyPath getFullPath(final String propertyName) {
        Objects.requireNonNull(basePropertyPath);
        Objects.requireNonNull(propertyName);
        return basePropertyPath.merge(propertyName);
    }

    /**
     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
     * object
     */
    @Override
    @JsonIgnore
    public String getFullPathStr(final String propertyName) {
        return getFullPath(propertyName).toString();
    }

    @Override
    @JsonIgnore
    public void setBasePath(final PropertyPath basePropertyPath) {
        this.basePropertyPath = Objects.requireNonNull(basePropertyPath);
    }

}
