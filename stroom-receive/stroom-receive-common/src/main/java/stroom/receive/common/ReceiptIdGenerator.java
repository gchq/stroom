package stroom.receive.common;

import stroom.util.concurrent.UniqueId;

/**
 * Generates a {@link UniqueId} for all received items.
 */
public interface ReceiptIdGenerator {

    UniqueId generateId();
}
