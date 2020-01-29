package stroom.config.global.shared;

import stroom.docref.SharedObject;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClusterConfigProperty implements SharedObject {
    private ConfigProperty configProperty;
    private Map<String, OverrideValue<String>> nodeToOverrideMap = new HashMap<>();

    @SuppressWarnings("unused") // for gwt serialisation
    ClusterConfigProperty() {
    }

    public ClusterConfigProperty(final ConfigProperty configProperty, final String nodeName) {
        this.configProperty = configProperty;
    }

    public ClusterConfigProperty(final ConfigProperty configProperty,
                                 final Map<String, OverrideValue<String>> nodeToOverrideMap) {
        Objects.requireNonNull(configProperty);
        Objects.requireNonNull(nodeToOverrideMap);
        this.configProperty = configProperty;
        this.nodeToOverrideMap.putAll(nodeToOverrideMap);
    }

    public void putYamlOverrideValue(final String nodeName, final OverrideValue<String> yamOverrideValue) {
        nodeToOverrideMap.put(nodeName, yamOverrideValue);
    }

    public Map<String, String> getYamlOverrideValues() {
        return nodeToOverrideMap.entrySet().stream()
                .filter(entry -> entry.getValue().hasOverride())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getValue().orElse(null)));
    }

    public Map<String, String> getEffectiveValues() {
        return nodeToOverrideMap.entrySet().stream()
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

    public static ClusterConfigProperty merge(final ClusterConfigProperty prop1, final ClusterConfigProperty prop2) {
        Objects.requireNonNull(prop1);
        Objects.requireNonNull(prop2);
        if (!prop1.configProperty.getName().equals(prop2.configProperty.getName())) {
            throw new RuntimeException();
        }
        var nodeToOverrideMap = new HashMap<>(prop1.nodeToOverrideMap);
        nodeToOverrideMap.putAll(prop2.nodeToOverrideMap);
        return new ClusterConfigProperty(this.configProperty, nodeToOverrideMap);
    }
}
