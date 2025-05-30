package stroom.proxy.app.security;

import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.shared.UserDesc;

import java.util.Optional;

public interface ProxyApiKeyService {

    Optional<UserDesc> verifyApiKey(final VerifyApiKeyRequest request);

}
