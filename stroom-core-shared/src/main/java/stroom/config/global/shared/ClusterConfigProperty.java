package stroom.config.global.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PropertyPath;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClusterConfigProperty {
    private ConfigProperty configProperty;
    private Map<String, OverrideValue<String>> nodeToOverrideMap = new HashMap<>();

    @SuppressWarnings("unused")
        // for gwt serialisation
    ClusterConfigProperty() {
    }

    public ClusterConfigProperty(final ConfigProperty configProperty) {
        this.configProperty = configProperty;
    }

    public ClusterConfigProperty(final ConfigProperty configProperty, final String nodeName) {
        this.configProperty = configProperty;
        this.nodeToOverrideMap.put(nodeName, configProperty.getYamlOverrideValue());
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
                .filter(entry -> entry.getValue().isHasOverride())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getValueAsOptional().orElse(null)));
    }

    @JsonProperty("id")
    public Integer getId() {
        return configProperty.getId();
    }

    @JsonProperty("version")
    public Integer getVersion() {
        return configProperty.getVersion();
    }

    @JsonProperty("createTimeMs")
    public Long getCreateTimeMs() {
        return configProperty.getCreateTimeMs();
    }

    @JsonProperty("createUser")
    public String getCreateUser() {
        return configProperty.getCreateUser();
    }

    @JsonProperty("updateTimeMs")
    public Long getUpdateTimeMs() {
        return configProperty.getUpdateTimeMs();
    }

    @JsonProperty("updateUser")
    public String getUpdateUser() {
        return configProperty.getUpdateUser();
    }

    @JsonProperty("name")
    public String getNameAsString() {
        return configProperty.getNameAsString();
    }

    @JsonIgnore
    public PropertyPath getName() {
        return configProperty.getName();
    }

    @JsonIgnore
    public Optional<String> getEffectiveValue() {
        return configProperty.getEffectiveValue();
    }

    public Optional<String> getEffectiveValue(final String nodeName) {
        return Optional.ofNullable(nodeToOverrideMap.get(nodeName))
                .flatMap(yamlOverride ->
                        ConfigProperty.getEffectiveValue(
                                this.getDefaultValue().orElse(null),
                                this.getDatabaseOverrideValue(),
                                yamlOverride));
    }

    @JsonIgnore
    public Optional<String> getEffectiveValueMasked() {
        return configProperty.getEffectiveValueMasked();
    }

    @JsonProperty("databaseOverrideValue")
    public OverrideValue<String> getDatabaseOverrideValue() {
        return configProperty.getDatabaseOverrideValue();
    }

    @JsonIgnore
    public boolean hasDatabaseOverride() {
        return configProperty.hasDatabaseOverride();
    }

    @JsonIgnore
    public Optional<String> getDefaultValue() {
        return configProperty.getDefaultValue();
    }

    public String getDescription() {
        return configProperty.getDescription();
    }

    @JsonProperty("isEditable")
    public boolean isEditable() {
        return configProperty.isEditable();
    }

    @JsonProperty("requireRestart")
    public boolean isRequireRestart() {
        return configProperty.isRequireRestart();
    }

    public boolean isRequireUiRestart() {
        return configProperty.isRequireUiRestart();
    }

    @JsonProperty("isPassword")
    public boolean isPassword() {
        return configProperty.isPassword();
    }

    @JsonProperty("source")
    public ConfigProperty.SourceType getSource() {
        return configProperty.getSource();
    }

    @JsonProperty("dataTypeName")
    public String getDataTypeName() {
        return configProperty.getDataTypeName();
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
            throw new RuntimeException("Cannot merge two properties with different names, " +
                    prop1.configProperty.getName() + " & " + prop2.configProperty.getName());
        }
        final Map<String, OverrideValue<String>> nodeToOverrideMap = new HashMap<>(prop1.nodeToOverrideMap);
        nodeToOverrideMap.putAll(prop2.nodeToOverrideMap);
        return new ClusterConfigProperty(prop1.configProperty, nodeToOverrideMap);
    }
}
