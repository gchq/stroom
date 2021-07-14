package stroom.util.shared;

/**
 * Super class for all stroom-proxy config pojos. Can be decorated with property path
 * information, e.g. stroom.path.home
 */
public abstract class AbstractProxyConfig extends AbstractConfig implements IsProxyConfig {

//    // Held in part form to reduce memory overhead as some parts will be used
//    // many times over all the config objects
//    @JsonIgnore
//    private PropertyPath basePropertyPath = PropertyPath.blank();
//
//    /**
//     * @return The base property path, e.g. "stroom.node" for this config object
//     */
//    @JsonIgnore
//    public String getBasePath() {
//        Objects.requireNonNull(basePropertyPath);
//        return basePropertyPath.toString();
//    }
//
//    /**
//     * @return The full property path, e.g. "stroom.node.status" for the named property on this config
//     * object
//     */
//    public String getFullPath(final String propertyName) {
//        Objects.requireNonNull(basePropertyPath);
//        Objects.requireNonNull(propertyName);
//        return basePropertyPath.merge(propertyName).toString();
//    }
//
//    @JsonIgnore
//    public void setBasePath(final PropertyPath basePropertyPath) {
//        this.basePropertyPath = basePropertyPath;
//    }
}
