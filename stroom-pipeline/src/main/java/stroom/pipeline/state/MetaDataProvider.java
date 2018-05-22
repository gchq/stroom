package stroom.pipeline.state;

import stroom.feed.MetaMap;

public interface MetaDataProvider {
    String get(String key);

    MetaMap getMetaData();
}
