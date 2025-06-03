package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.shared.UserDesc;

import java.util.Optional;

public interface ProxyApiKeyService {

    Optional<UserDesc> verifyApiKey(final VerifyApiKeyRequest request);

    Optional<UserIdentity> verifyIdentity(final String apiKey);
}
