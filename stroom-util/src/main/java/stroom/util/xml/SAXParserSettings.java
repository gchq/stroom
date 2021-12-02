package stroom.util.xml;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class SAXParserSettings {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SAXParserSettings.class);

    private static final AtomicBoolean SECURE_PROCESSING = new AtomicBoolean(true);
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);

    public static boolean isSecureProcessingEnabled() {
        try {
            // Need to ensure nothing gets the value until it has been set to something.
            LOGGER.debug("Waiting for isSecureProcessingEnabled to be set");
            countDownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for isSecureProcessingEnabled to be set");
        }
        return SECURE_PROCESSING.get();
    }

    public static void setSecureProcessingEnabled(final boolean isEnabled) {
        if (countDownLatch.getCount() > 0) {
            SECURE_PROCESSING.set(isEnabled);
            countDownLatch.countDown();
        }
    }
}
