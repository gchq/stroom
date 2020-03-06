package stroom.pipeline.state;

import stroom.meta.api.AttributeMap;

public interface MetaDataProvider {
    String get(String key);

    AttributeMap getMetaData();
}
