package stroom.receive.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import java.util.function.Consumer;

public class ProgressHandler implements Consumer<Long> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProgressHandler.class);

    private final String prefix;
    private long totalBytes;

    public ProgressHandler(final String prefix) {
        this.prefix = prefix + " - ";
    }

    @Override
    public void accept(final Long bytes) {
        if (LOGGER.isTraceEnabled()) {
            totalBytes += bytes;
            LOGGER.trace(() -> prefix +
                    ModelStringUtil.formatIECByteSizeString(
                            totalBytes));
        }
    }
}
