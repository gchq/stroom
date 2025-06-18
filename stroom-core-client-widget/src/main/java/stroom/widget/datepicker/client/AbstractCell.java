package stroom.widget.datepicker.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Widget;

public abstract class AbstractCell extends Widget {

    private final Element cellInner;

    public AbstractCell() {
        final Element cellOuter = DOM.createDiv();
        cellOuter.setClassName("cellOuter");
        cellInner = DOM.createDiv();
        cellInner.setClassName("cellInner");
        DOM.appendChild(cellOuter, cellInner);
        setElement(cellOuter);
    }

    protected void setText(final String value) {
        cellInner.setInnerText(value);
    }
}
