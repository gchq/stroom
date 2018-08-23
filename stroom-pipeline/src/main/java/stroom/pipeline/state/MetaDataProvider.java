package stroom.pipeline.state;

import stroom.data.meta.api.AttributeMap;

public interface MetaDataProvider {
    String get(String key);

    AttributeMap getMetaData();
}
