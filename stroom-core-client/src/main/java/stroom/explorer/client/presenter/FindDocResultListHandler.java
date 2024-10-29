package stroom.explorer.client.presenter;

import stroom.explorer.shared.FindResult;

public interface FindDocResultListHandler<T> {

    void openDocument(T match);

    void focus();
}
