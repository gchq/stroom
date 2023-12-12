package stroom.security.impl;

public record HashedApiKeyParts(String saltedApiKeyHash,
                                String salt,
                                String apiKeyPrefix) {

}
