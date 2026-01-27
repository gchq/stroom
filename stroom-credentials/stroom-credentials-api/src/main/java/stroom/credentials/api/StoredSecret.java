package stroom.credentials.api;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.Secret;

public record StoredSecret(Credential credential, Secret secret, byte[] keyStore) {


}
