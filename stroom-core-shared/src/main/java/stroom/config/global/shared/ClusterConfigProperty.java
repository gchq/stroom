package stroom.config.global.shared;

import stroom.docref.SharedObject;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClusterConfigProperty implements SharedObject {
    private ConfigProperty configProperty;
    private Map<String, OverrideValue<String>> yamlOverrides = new HashMap<>();

    @SuppressWarnings("unused") // for gwt serialisation
    ClusterConfigProperty() {
    }

    public ClusterConfigProperty(final ConfigProperty configProperty) {
        this.configProperty = configProperty;
    }

    public void putYamlOverrideValue(final String nodeName, final OverrideValue<String> yamOverrideValue) {
        yamlOverrides.put(nodeName, yamOverrideValue);
    }

    public Map<String, String> getYamlOverrideValues() {
        return yamlOverrides.entrySet().stream()
                .filter(entry -> entry.getValue().hasOverride())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getValue().orElse(null)));
    }

    public Map<String, String> getEffectiveValues() {
        return yamlOverrides.entrySet().stream()
                .map(entry -> {
                    Optional<String> effectiveValue = ConfigProperty.getEffectiveValue(
                            configProperty.getDefaultValue().orElse(null),
                            configProperty.getDatabaseOverrideValue(),
                            entry.getValue());
                    return new AbstractMap.SimpleEntry<>(entry.getKey(), effectiveValue);
                })
                .filter(entry -> entry.getValue().isPresent())
                .collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        entry -> entry.getValue().get()));
    }


}
