package stroom.data.grid.client;

import com.google.gwt.dom.client.Element;

public class Heading {

    private final Element tableElement;
    private final Element element;
    private final int colIndex;
    private final int initialX;

    public Heading(final Element tableElement, final Element element, final int colIndex, final int initialX) {
        this.tableElement = tableElement;
        this.element = element;
        this.colIndex = colIndex;
        this.initialX = initialX;
    }

    public Element getTableElement() {
        return tableElement;
    }

    public Element getElement() {
        return element;
    }

    public int getColIndex() {
        return colIndex;
    }

    public int getInitialX() {
        return initialX;
    }
}
