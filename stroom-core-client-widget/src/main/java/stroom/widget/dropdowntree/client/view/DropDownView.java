package stroom.widget.dropdowntree.client.view;

import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public interface DropDownView extends View, HasUiHandlers<DropDownUiHandlers> {

    void setText(String text);
}