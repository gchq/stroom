package stroom.query.client.presenter;

import stroom.util.shared.ErrorMessage;

import java.util.List;

public interface SearchErrorListener {
    void onError(List<ErrorMessage> errors);
}
