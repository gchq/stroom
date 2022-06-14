package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;

public class StroomStreamStatus {

    private final StroomStatusCode stroomStatusCode;
    private final AttributeMap attributeMap;

    public StroomStreamStatus(final StroomStatusCode stroomStatusCode,
                              final AttributeMap attributeMap) {
        this.stroomStatusCode = stroomStatusCode;
        this.attributeMap = attributeMap;
    }

    public StroomStatusCode getStroomStatusCode() {
        return stroomStatusCode;
    }

    public AttributeMap getAttributeMap() {
        return attributeMap;
    }

    public String buildStatusMessage(final Object... args) {
        final StringBuilder builder = new StringBuilder();
        builder.append("Stroom Status ");
        if (stroomStatusCode != null) {
            builder.append(stroomStatusCode.getCode())
                    .append(" - ")
                    .append(stroomStatusCode.getMessage());
        } else {
            builder.append("null");
        }

        AttributeMapUtil.appendAttributes(
                attributeMap,
                builder,
                StandardHeaderArguments.FEED,
                StandardHeaderArguments.COMPRESSION,
                StandardHeaderArguments.TYPE);

        if (args != null) {
            for (final Object object : args) {
                builder.append(" - ")
                        .append(object);
            }
        }
        return builder.toString();
    }


    public String toString() {
        final StringBuilder clientDetailsStringBuilder = new StringBuilder();
        AttributeMapUtil.appendAttributes(
                attributeMap,
                clientDetailsStringBuilder,
                StandardHeaderArguments.X_FORWARDED_FOR,
                StandardHeaderArguments.REMOTE_HOST,
                StandardHeaderArguments.REMOTE_ADDRESS,
                StandardHeaderArguments.RECEIVED_PATH);

        final String clientDetailsStr = clientDetailsStringBuilder.isEmpty()
                ? ""
                : " - " + clientDetailsStringBuilder;

        return stroomStatusCode.getHttpCode() + " - " + buildStatusMessage() + clientDetailsStr;
    }
}
