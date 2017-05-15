package stroom.editor.client.view;

import edu.ycp.cs.dh.acegwt.client.ace.AceAnnotationType;

public class Annotation {
    private final int row;
    private final int column;
    private final String text;
    private final AceAnnotationType type;

    public Annotation(final int row, final int column, final String text, final AceAnnotationType type) {
        this.row = row;
        this.column = column;
        this.text = text;
        this.type = type;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getText() {
        return text;
    }

    public AceAnnotationType getType() {
        return type;
    }
}
