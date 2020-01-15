package stroom.config.global.shared;

import stroom.docref.SharedObject;

public class NodeConfigResult implements SharedObject {
    private static final long serialVersionUID = 4454645971807552635L;

    private final String nodeName;
    private final OverrideValue<String> yamlOverrideValue;

    public NodeConfigResult(final String nodeName, final OverrideValue<String> yamlOverrideValue) {
        this.nodeName = nodeName;
        this.yamlOverrideValue = yamlOverrideValue;
    }

    public String getNodeName() {
        return nodeName;
    }

    public OverrideValue<String> getYamlOverrideValue() {
        return yamlOverrideValue;
    }

    @Override
    public String toString() {
        return "NodePropertyYamlOverride{" +
                "nodeName='" + nodeName + '\'' +
                ", yamlOverrideValue=" + yamlOverrideValue +
                '}';
    }
}
