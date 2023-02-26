package stroom.widget.menu.client.presenter;

import com.google.gwt.dom.client.Element;

public interface MenuItemCellUiHandler {
    void onClick(MenuItem menuItem, Element element);

    void onMouseOver(MenuItem menuItem, Element element);

    void onMouseOut(MenuItem menuItem, Element element);

    boolean isHover(MenuItem menuItem);

    boolean isHighlighted(MenuItem menuItem);
}
