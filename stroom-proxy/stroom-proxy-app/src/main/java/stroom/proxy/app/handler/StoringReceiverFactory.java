package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.StreamUtil;
import stroom.util.shared.NullSafe;

public class StoringReceiverFactory implements ReceiverFactory {

    private final SimpleReceiver simpleReceiver;
    private final ZipReceiver zipReceiver;

    public StoringReceiverFactory(final SimpleReceiver simpleReceiver,
                                  final ZipReceiver zipReceiver) {
        this.simpleReceiver = simpleReceiver;
        this.zipReceiver = zipReceiver;
    }

    @Override
    public Receiver get(final AttributeMap attributeMap) {
        // Treat differently depending on compression type.
        final String key = StandardHeaderArguments.COMPRESSION;
        String compression = attributeMap.get(key);
        if (NullSafe.isNonEmptyString(compression)) {
            compression = compression.toUpperCase(StreamUtil.DEFAULT_LOCALE);
            // Put the normalised value back in the map
            attributeMap.put(key, compression);
            if (!StandardHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                throw new StroomStreamException(
                        StroomStatusCode.UNKNOWN_COMPRESSION, attributeMap, compression);
            }
        }

        if (StandardHeaderArguments.COMPRESSION_ZIP.equalsIgnoreCase(compression)) {
            // Handle a zip stream.
            return zipReceiver;

        } else {
            // Handle non zip streams.
            return simpleReceiver;
        }
    }
}
