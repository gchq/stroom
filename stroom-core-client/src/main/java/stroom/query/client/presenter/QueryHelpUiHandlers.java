package stroom.query.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

public interface QueryHelpUiHandlers extends UiHandlers {

    void changeQuickFilter(String name);

    void onInsert();

    void onCopy();
}
