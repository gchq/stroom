package stroom.query.client.presenter;

import stroom.util.shared.TokenError;

public interface TokenErrorListener {

    void onError(TokenError error);
}
