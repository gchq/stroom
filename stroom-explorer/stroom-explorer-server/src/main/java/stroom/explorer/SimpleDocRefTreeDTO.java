package stroom.explorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple doc ref tree that can be used to populate the Doc Explorer in the UI.
 */
class SimpleDocRefTreeDTO {
    private final String uuid;
    private final String type;
    private final String name;

    private final List<SimpleDocRefTreeDTO> children;

    SimpleDocRefTreeDTO(final String uuid,
                        final String type,
                        final String name) {
        this.uuid = uuid;
        this.type = type;
        this.name = name;
        this.children = new ArrayList<>();
    }

    public String getUuid() {
        return uuid;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public List<SimpleDocRefTreeDTO> getChildren() {
        return (children.size() > 0) ? children : null;
    }

    public void addChild(final SimpleDocRefTreeDTO child) {
        this.children.add(child);
    }
}
