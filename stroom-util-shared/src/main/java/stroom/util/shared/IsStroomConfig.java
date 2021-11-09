package stroom.util.shared;

/**
 * Marker interface for config classes used by Stroom.
 * Helps with ensuring all config classes are bound.
 * Used to distinguish between config classes that are used by proxy.
 * Config classes can implement {@link IsStroomConfig} and IsProxyConfig if
 * they are shared.
 */
public interface IsStroomConfig extends HasPropertyPath {

}
