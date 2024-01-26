package stroom.analytics.client.presenter;

import stroom.document.client.event.DirtyUiHandlers;

public interface ProcessingStatusUiHandlers extends DirtyUiHandlers {

    void onRefreshProcessingStatus();

}
