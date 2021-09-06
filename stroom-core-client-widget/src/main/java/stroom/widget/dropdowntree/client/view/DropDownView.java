package stroom.widget.dropdowntree.client.view;

import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public interface DropDownView extends View, Focus, HasUiHandlers<DropDownUiHandlers> {

    void setText(String text);
}
