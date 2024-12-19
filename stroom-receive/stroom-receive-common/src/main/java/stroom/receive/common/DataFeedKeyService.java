package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

public interface DataFeedKeyService extends AttributeMapFilter, AuthenticatorFilter {

    Optional<DataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                         final AttributeMap attributeMap);
}
