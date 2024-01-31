package stroom.explorer.client.presenter;

import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

    void setResultView(View view);
}
