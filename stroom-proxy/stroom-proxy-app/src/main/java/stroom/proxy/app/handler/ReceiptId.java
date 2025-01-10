package stroom.proxy.app.handler;

import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record ReceiptId(String proxyId, String eventId) {

    public static final String RECEIPT_ID_DELIMITER = "_";
    public static final Pattern RECEIPT_ID_DELIMITER_PATTERN = Pattern.compile("_");

    public static ReceiptId generate(final String proxyId) {
        return new ReceiptId(
                Objects.requireNonNull(proxyId),
                UUID.randomUUID().toString());
    }

    /**
     * Parse a {@link ReceiptId} from a string.
     */
    public static ReceiptId parse(final String receiptIdStr) {
        final String trimmed = NullSafe.trim(receiptIdStr);
        if (NullSafe.isEmptyString(trimmed)) {
            return null;
        } else {
            if (!receiptIdStr.contains(RECEIPT_ID_DELIMITER)) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Invalid receiptIdStr '{}', no '{}' found",
                        receiptIdStr, RECEIPT_ID_DELIMITER));
            }
            final String[] parts = RECEIPT_ID_DELIMITER_PATTERN.split(trimmed);
            if (parts.length != 2) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Invalid receiptIdStr '{}', expecting two parts when splitting on '{}'",
                        trimmed, RECEIPT_ID_DELIMITER));
            }
            return new ReceiptId(parts[0], parts[1]);
        }
    }

    @Override
    public String toString() {
        return proxyId + RECEIPT_ID_DELIMITER + eventId;
    }
}
