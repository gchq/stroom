package stroom.util.metrics;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMetricsUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetricsUtil.class);

    private final String name = MetricsUtil.buildName(getClass(), "foo", "bar");

    @Test
    void testBuildName1() {
        final String name = MetricsUtil.buildName(getClass(), "foo", "bar");
        Assertions.assertThat(name)
                .isEqualTo(getClass().getName() + "." + "foo.bar");
    }

    @Test
    void testBuildName2() {
        final String name = MetricsUtil.buildName(getClass(), "foo bar%");
        Assertions.assertThat(name)
                .isEqualTo(getClass().getName() + "." + "foobar");
    }

    @Test
    void testBuildName3() {
        final String name = MetricsUtil.buildName(getClass());
        Assertions.assertThat(name)
                .isEqualTo(getClass().getName());
    }
}
