package stroom.data.store.api;

import stroom.meta.api.AttributeMap;

import java.util.Map;

public interface AttributeMapFactory {

    Map<String, String> getAttributes(long metaId);

    AttributeMap getAttributeMapForPart(final long streamId, final long partNo);
}
