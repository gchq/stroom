package stroom.proxy.repo;

import stroom.feed.MetaMap;

import java.io.IOException;

public interface StroomHeaderStreamHandler {
    void handleHeader(MetaMap metaMap) throws IOException;

}
