package stroom.widget.menu.client.presenter;

import com.google.gwt.dom.client.Element;
import com.gwtplatform.mvp.client.UiHandlers;

public interface MenuUiHandlers extends UiHandlers {

    void showSubMenu(MenuItem menuItem, Element element);

    void ensureParentItemSelected();

    void focusSubMenu();

    void focusParent(final boolean hideChildren);

    boolean hasParent();

    void escape();

    void execute(MenuItem menuItem);
}
