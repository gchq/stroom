package stroom.config.common;

import java.net.URI;

public interface UriFactory {
    String localUriString(String path);

    URI localURI(String path);

    String publicUriString(String path);

    URI publicURI(String path);
}
