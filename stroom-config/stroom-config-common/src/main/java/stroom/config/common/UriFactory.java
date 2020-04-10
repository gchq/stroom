package stroom.config.common;

import java.net.URI;

public interface UriFactory {
    URI localUri(String path);

    URI publicUri(String path);

    URI uiUri(String path);
}
