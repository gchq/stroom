package stroom.data.store.impl;

import stroom.meta.shared.Meta;

import java.util.Map;

public interface AttributeMapFactory {
    Map<String, String> getAttributes(Meta meta);
}