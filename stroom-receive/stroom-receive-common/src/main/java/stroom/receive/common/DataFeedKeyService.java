package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import java.nio.file.Path;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface DataFeedKeyService extends AuthenticatorFilter {

    Optional<DataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                         final AttributeMap attributeMap);

    Optional<DataFeedKey> getDataFeedKey(final String subjectId);

    void addDataFeedKeys(DataFeedKeys dataFeedKeys,
                         Path sourceFile);

    void evictExpired();

    void removeKeysForFile(Path sourceFile);
}
