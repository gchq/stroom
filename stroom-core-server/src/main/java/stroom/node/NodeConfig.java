package stroom.node;

import stroom.properties.api.PropertyService;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NodeConfig {
    private String nodeName;
    private String rackName;

    public NodeConfig() {
    }

    @Inject
    public NodeConfig(final PropertyService propertyService) {
        this.nodeName = propertyService.getProperty("stroom.node");
        this.rackName = propertyService.getProperty("stroom.rack");
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public String getRackName() {
        return rackName;
    }

    public void setRackName(final String rackName) {
        this.rackName = rackName;
    }
}
