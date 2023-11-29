package stroom.security.impl;

import stroom.security.common.impl.ClientCredentials;

public interface IdpProxyService {

    String fetchToken(final ClientCredentials clientCredentials);

}
