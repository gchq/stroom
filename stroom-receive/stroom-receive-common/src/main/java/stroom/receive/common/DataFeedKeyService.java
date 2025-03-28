package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.file.Path;
import java.util.Optional;

public interface DataFeedKeyService extends AuthenticatorFilter {

    Optional<HashedDataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                               final AttributeMap attributeMap);

    Optional<HashedDataFeedKey> getLatestDataFeedKey(final String accountId);

    int addDataFeedKeys(HashedDataFeedKeys hashedDataFeedKeys,
                        Path sourceFile);

    void evictExpired();

    void removeKeysForFile(Path sourceFile);
}
