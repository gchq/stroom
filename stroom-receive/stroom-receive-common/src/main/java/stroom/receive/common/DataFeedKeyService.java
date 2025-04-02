package stroom.receive.common;

import java.nio.file.Path;

public interface DataFeedKeyService extends AuthenticatorFilter {

//    Optional<HashedDataFeedKey> getDataFeedKey(final HttpServletRequest request,
//                                               final AttributeMap attributeMap);

//    Optional<HashedDataFeedKey> getLatestDataFeedKey(final String accountId);

    int addDataFeedKeys(HashedDataFeedKeys hashedDataFeedKeys,
                        Path sourceFile);

    void evictExpired();

    void removeKeysForFile(Path sourceFile);
}
