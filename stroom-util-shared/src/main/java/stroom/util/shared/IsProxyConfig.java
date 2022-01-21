package stroom.util.shared;

/**
 * Marker interface for config classes used by Stroom Proxy.
 * Helps with ensuring all config classes are bound.
 * Used to distinguish between config classes that are used by stroom.
 * Config classes can implement {@link IsStroomConfig} and {@link IsProxyConfig} if
 * they are shared.
 */
public interface IsProxyConfig extends HasPropertyPath {

}
