package stroom.query.client.presenter;

import com.gwtplatform.mvp.client.UiHandlers;

public interface QueryDataUiHandlers extends UiHandlers {
    void onRun();

    void onStop();

    void onReset();

    void onCreateDashboard();
}
