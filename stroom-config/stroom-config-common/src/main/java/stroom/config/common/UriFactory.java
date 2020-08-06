package stroom.config.common;

import java.net.URI;

public interface UriFactory {
    /**
     * Creates a URI that can be used to connect to this application directly from another node or from this one.
     * It should not return localhost in a multi node environment but instead provide an FQDN.
     * The URI is for direct connection to the application and not via a proxy.
     *
     * @param path The path of the URI.
     * @return A URI to connect directly to the application.
     */
    URI nodeUri(String path);

    /**
     * If the application is served by a proxy, e.g. NGINX, then the public facing URI will be different from the
     * nodeUri. In this case the public URI needs to be configured.
     *
     * If not configured this will return the same thing as nodeUri.
     *
     * @param path The path of the URI.
     * @return A URI to connect to the application from beyond a proxy.
     */
    URI publicUri(String path);

    /**
     * If the UI is being served separately from the rest of the application, e.g. when developing React code
     * externally, then this URI must point to the URL serving the UI.
     *
     * If not configured this will return the same thing as publicUri.
     *
     * @param path The path of the URI.
     * @return A URI for the UI.
     */
    URI uiUri(String path);
}
