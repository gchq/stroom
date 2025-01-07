package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.StreamUtil;

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
        String compression = attributeMap.get(StandardHeaderArguments.COMPRESSION);
        if (compression != null && !compression.isEmpty()) {
            compression = compression.toUpperCase(StreamUtil.DEFAULT_LOCALE);
            if (!StandardHeaderArguments.VALID_COMPRESSION_SET.contains(compression)) {
                throw new StroomStreamException(
                        StroomStatusCode.UNKNOWN_COMPRESSION, attributeMap, compression);
            }
        }

        if (StandardHeaderArguments.COMPRESSION_ZIP.equals(compression)) {
            // Handle a zip stream.
            return zipReceiver;

        } else {
            // Handle non zip streams.
            return simpleReceiver;
        }
    }
}
