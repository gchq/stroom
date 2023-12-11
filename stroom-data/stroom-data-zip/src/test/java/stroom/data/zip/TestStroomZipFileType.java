package stroom.data.zip;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestStroomZipFileType {

    @TestFactory
    Stream<DynamicTest> testHasExtension() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StroomZipFileType.META::hasExtension)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase("foo", false)
                .addCase("foometa", false)
                .addCase("foo.bar", false)
                .addCase("foo.meta", true)
                .addCase("foo.hdr", true)
                .addCase("foo.header", true)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testHasOfficialExtension() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StroomZipFileType.META::hasOfficialExtension)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase("foo", false)
                .addCase("foometa", false)
                .addCase("foo.bar", false)
                .addCase("foo.meta", true)
                .addCase("foo.hdr", false)
                .addCase("foo.header", false)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testIsKnownExtension() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(StroomZipFileType::isKnownExtension)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase("foo", false)
                .addCase("meta", true)
                .addCase("hdr", true)
                .addCase("header", true)
                .build();
    }
}
