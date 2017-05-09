package stroom.editor.client.view;

import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

public class Marker {
    private final AceRange range;
    private final String clazz;
    private final AceMarkerType type;
    private final boolean inFront;

    public Marker(final AceRange range, final String clazz, final AceMarkerType type, final boolean inFront) {
        this.range = range;
        this.clazz = clazz;
        this.type = type;
        this.inFront = inFront;
    }

    public AceRange getRange() {
        return range;
    }

    public String getClazz() {
        return clazz;
    }

    public AceMarkerType getType() {
        return type;
    }

    public boolean isInFront() {
        return inFront;
    }
}
