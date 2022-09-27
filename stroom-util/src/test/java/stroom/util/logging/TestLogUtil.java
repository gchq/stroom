package stroom.util.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestLogUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLogUtil.class);

    @Test
    void inSeparatorLine() {
        LOGGER.info(LogUtil.inSeparatorLine("Hello World"));
    }

    @Test
    void inBox() {
        LOGGER.info(LogUtil.inBox("Hello World"));
    }
}
