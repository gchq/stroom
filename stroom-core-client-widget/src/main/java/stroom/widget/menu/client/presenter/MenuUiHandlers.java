package stroom.widget.menu.client.presenter;

import com.google.gwt.dom.client.Element;
import com.gwtplatform.mvp.client.UiHandlers;

public interface MenuUiHandlers extends UiHandlers {

    void showSubMenu(MenuItem menuItem, Element element);

    void focusSubMenu();

    void focusParent();

    void escape();

    void execute(CommandMenuItem menuItem);
}
