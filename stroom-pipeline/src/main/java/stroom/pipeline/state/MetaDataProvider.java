package stroom.pipeline.state;

import stroom.feed.AttributeMap;

public interface MetaDataProvider {
    String get(String key);

    AttributeMap getMetaData();
}
