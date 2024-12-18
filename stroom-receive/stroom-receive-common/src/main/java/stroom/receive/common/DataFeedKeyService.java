package stroom.receive.common;

import stroom.meta.api.AttributeMap;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface DataFeedKeyService extends AttributeMapFilter, AuthenticatorFilter {

    Optional<DataFeedKey> getDataFeedKey(final HttpServletRequest request,
                                         final AttributeMap attributeMap);
}
