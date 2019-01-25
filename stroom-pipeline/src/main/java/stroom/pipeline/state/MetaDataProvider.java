package stroom.pipeline.state;

import stroom.data.meta.shared.AttributeMap;

public interface MetaDataProvider {
    String get(String key);

    AttributeMap getMetaData();
}
