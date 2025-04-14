package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestProxyZipValidator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyZipValidator.class);

    @Test
    void test1() {
        doTest(true,
                "0000000001.mf",
                "0000000001.meta",
                "0000000001.ctx",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.ctx",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test2_noMeta() {
        doTest(false,
                "0000000001.dat",
                "0000000002.dat",
                "0000000003.dat");
    }

    @Test
    void test3_minimal() {
        doTest(true,
                "0000000001.meta",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test3_minimalWithManifest() {
        doTest(true,
                "0000000001.mf",
                "0000000001.meta",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test4_oneMissingMeta() {
        doTest(false,
                "0000000001.meta",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.dat",
                "0000000003.dat");
    }

    @Test
    void test5_manifestInWrongPlace() {
        doTest(false,
                "0000000001.meta",
                "0000000001.mf",
                "0000000001.ctx",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.ctx",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test6_manifestInWrongPlace() {
        doTest(false,
                "0000000001.meta",
                "0000000001.ctx",
                "0000000001.dat",
                "0000000002.mf", // bad
                "0000000002.meta",
                "0000000002.ctx",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test7_contextInWrongPlace() {
        doTest(false,
                "0000000001.mf",
                "0000000001.meta",
                "0000000001.ctx",
                "0000000001.dat",
                "0000000002.ctx", // bad
                "0000000002.meta",
                "0000000002.dat",
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test7_missingDat() {
        doTest(false,
                "0000000001.mf",
                "0000000001.meta",
                "0000000001.ctx",
                "0000000001.dat",
                "0000000002.meta",
                "0000000002.ctx",
                // missing
                "0000000003.meta",
                "0000000003.dat");
    }

    @Test
    void test8_badBaseName() {
        doTest(false,
                "1.meta",
                "1.dat",
                "2.meta",
                "2.dat",
                "3.meta",
                "3.dat");
    }

    @Test
    void test9_badExtension() {
        doTest(false,
                "1.meta",
                "1.dat",
                "2.meta",
                "2.foo",
                "2.dat",
                "3.meta",
                "3.dat");
    }

    @Test
    void test10_noEntries() {
        doTest(false);
    }

    private String doTest(final boolean expectedIsValid, final String... entries) {
        final ProxyZipValidator proxyZipValidator = new ProxyZipValidator();
        NullSafe.asList(entries)
                .forEach(proxyZipValidator::addEntry);

        final boolean valid = proxyZipValidator.isValid();
        final String errorMessage = proxyZipValidator.getErrorMessage();
        LOGGER.info("errorMessage: '{}'", errorMessage);

        Assertions.assertThat(valid)
                .isEqualTo(expectedIsValid);

        if (expectedIsValid) {
            Assertions.assertThat(errorMessage)
                    .isNull();
        } else {
            Assertions.assertThat(errorMessage)
                    .isNotNull();
        }

        return errorMessage;
    }
}
