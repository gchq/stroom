package stroom.query.language.functions;

import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestHostName extends AbstractFunctionTest<HostName> {

    @Override
    Class<HostName> getFunctionType() {
        return HostName.class;
    }

    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of("localhost", "localhost", "localhost"),
                TestCase.of("public domain", "google.com", "google.com"),
                TestCase.of("ip address", "dns.google", "8.8.8.8"),
                TestCase.of("unknown host", ValErr.create("a.b.c.d.invalid.host"),
                        ValString.create("a.b.c.d.invalid.host"))
        );
    }
}
