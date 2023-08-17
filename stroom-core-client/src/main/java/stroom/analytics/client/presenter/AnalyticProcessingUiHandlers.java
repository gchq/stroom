package stroom.analytics.client.presenter;

import stroom.document.client.event.DirtyUiHandlers;

public interface AnalyticProcessingUiHandlers extends DirtyUiHandlers {

    void onRefreshProcessingStatus();

    void onProcessingTypeChange();

}
