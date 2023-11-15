package stroom.data.zip;

import stroom.data.zip.StroomZipEntries.StroomZipEntryGroup;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

class TestStroomZipEntries {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStroomZipEntries.class);

    @Test
    void addFile() {
        final StroomZipEntries stroomZipEntries = new StroomZipEntries();
        stroomZipEntries.addFile("2023-11-15.xyz.1001");
        stroomZipEntries.addFile("2023-11-15.xyz.1002");
        stroomZipEntries.addFile("2023-11-15.xyz.1003");
        stroomZipEntries.addFile("2023-11-15.xyz.1004");
    }

    @TestFactory
    Stream<DynamicTest> testAddFile() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<List<String>>() {
                })
                .withWrappedOutputType(new TypeLiteral<List<String>>() {
                })
                .withSingleArgTestFunction(fileNames -> {
                    final StroomZipEntries stroomZipEntries = new StroomZipEntries();
                    for (final String fileName : fileNames) {
                        stroomZipEntries.addFile(fileName);
                    }
                    LOGGER.info("stroomZipEntries: {}", stroomZipEntries);
                    return stroomZipEntries.getBaseNames();
                })
                .withSimpleEqualityAssertion()
                // This case should work
                .addCase(
                        List.of(
                                "2023-11-15.xyz.1001",
                                "2023-11-15.xyz.1002",
                                "2023-11-15.xyz.1003",
                                "2023-11-15.xyz.1004"),
                        List.of(
                                "2023-11-15.xyz.1001",
                                "2023-11-15.xyz.1002",
                                "2023-11-15.xyz.1003",
                                "2023-11-15.xyz.1004"))
                .addCase(List.of("request.dat", "request.hdr"),
                        List.of("request"))
                .addCase(List.of("request", "request.hdr"),
                        List.of("request"))
                .addCase(List.of("001.data", "001.ctx"),
                        List.of("001"))
                .addCase(List.of("001", "001.ctx"),
                        List.of("001"))
                .addCase(List.of("001.unknown", "001.ctx"),
                        List.of("001"))
                .addCase(List.of("abc.001", "abc.001.ctx"),
                        List.of("abc.001"))
                .addCase(List.of("abc.001.unknown", "abc.001.ctx"),
                        List.of("abc.001"))
                .addCase(List.of("001.dat", "002.dat", "003.dat", "002.ctx"),
                        List.of("001", "002", "003"))
                .addCase(List.of("1", "1.hdr", "11", "11.hdr"),
                        List.of("1", "11"))
                .addCase(List.of("1", "1.ctx", "1.hdr", "11", "11.ctx", "11.hdr"),
                        List.of("1", "11"))
                .addCase(List.of("1", "11", "111", "111.hdr", "11.hdr", "1.hdr"),
                        List.of("1", "11", "111"))
                .addCase(List.of("111.ctx",
                        "11.ctx",
                        "1.ctx",
                        "111.hdr",
                        "11.hdr",
                        "1.hdr",
                        "111.log",
                        "11.log",
                        "1.log"), List.of("111", "11", "1"))
                .addCase(List.of("111.ctx",
                        "11.ctx",
                        "1.ctx",
                        "111.hdr",
                        "11.hdr",
                        "1.hdr",
                        "1.log",
                        "11.log",
                        "111.log"), List.of("111", "11", "1"))
                .addCase(List.of("111.log",
                                "11.log",
                                "1.log",
                                "111.ctx",
                                "11.ctx",
                                "1.ctx",
                                "111.hdr",
                                "11.hdr",
                                "1.hdr"),
                        List.of("111", "11", "1"))
                .addCase(List.of("2.dat", "1.dat", "2.meta", "1.meta"),
                        List.of("2", "1"))
                .addThrowsCase(List.of("001", "001.ctx", "001.dat"), StroomZipNameException.class)
                .build();
    }

    @Test
    void testCloneWithNewBaseName() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001.mydata");
        final StroomZipEntry oldZipEntry = StroomZipEntry.createFromFileName("001.mydata");
        zipEntryGroup.add(oldZipEntry);

        final StroomZipEntryGroup newZipEntryGroup = zipEntryGroup.cloneWithNewBaseName("001");

        final StroomZipEntry newZipEntry = newZipEntryGroup.getByType(StroomZipFileType.DATA).orElseThrow();

        Assertions.assertThat(newZipEntry.getBaseName())
                .isEqualTo("001");
        Assertions.assertThat(newZipEntry.getFullName())
                .isEqualTo("001.mydata")
                .isEqualTo(oldZipEntry.getFullName());
        Assertions.assertThat(newZipEntry.getStroomZipFileType())
                .isEqualTo(StroomZipFileType.DATA)
                .isEqualTo(oldZipEntry.getStroomZipFileType());
    }
}
