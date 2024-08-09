package stroom.explorer.client.presenter;

import stroom.explorer.shared.FindResult;

public interface FindDocResultListHandler {

    void openDocument(FindResult match);

    void focus();
}
