package stroom.query.client.presenter;

import stroom.widget.presenter.client.UiHandlers;

public interface QueryDataUiHandlers extends UiHandlers {
    void onRun();

    void onStop();

    void onReset();

    void onCreateDashboard();
}
