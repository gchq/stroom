package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.NullSafe;

import java.util.Objects;

public class ForwardException extends RuntimeException {

    private final StroomStatusCode stroomStatusCode;
    private final String feedName;
    private final boolean isRecoverable;

    private ForwardException(final StroomStatusCode stroomStatusCode,
                             final AttributeMap attributeMap,
                             final String message,
                             final boolean isRecoverable,
                             final Throwable cause) {
        super(message, cause);
        this.isRecoverable = isRecoverable;
        this.stroomStatusCode = stroomStatusCode;
        this.feedName = NullSafe.get(
                attributeMap,
                attrMap -> attrMap.get(StandardHeaderArguments.FEED));
    }

    public static ForwardException recoverable(final StroomStatusCode stroomStatusCode,
                                               final AttributeMap attributeMap,
                                               final String message,
                                               final Throwable cause) {
        return new ForwardException(stroomStatusCode, attributeMap, message, true, cause);
    }

    public static ForwardException recoverable(final StroomStatusCode stroomStatusCode,
                                               final AttributeMap attributeMap) {
        Objects.requireNonNull(stroomStatusCode);
        return new ForwardException(stroomStatusCode, attributeMap, stroomStatusCode.getMessage(), true, null);
    }

    public static ForwardException nonRecoverable(final StroomStatusCode stroomStatusCode,
                                                  final AttributeMap attributeMap,
                                                  final String message,
                                                  final Throwable cause) {
        return new ForwardException(stroomStatusCode, attributeMap, message, false, cause);
    }

    public static ForwardException nonRecoverable(final StroomStatusCode stroomStatusCode,
                                                  final AttributeMap attributeMap) {
        Objects.requireNonNull(stroomStatusCode);
        return new ForwardException(stroomStatusCode, attributeMap, stroomStatusCode.getMessage(), false, null);
    }

    public boolean isRecoverable() {
        return isRecoverable;
    }

    public String getFeedName() {
        return feedName;
    }
}
