package stroom.meta.api;

import stroom.meta.shared.Meta;

import java.util.Map;

public interface AttributeMapFactory {
    Map<String, String> getAttributes(Meta meta);
}
