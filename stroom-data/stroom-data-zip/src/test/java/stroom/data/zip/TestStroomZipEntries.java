/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.zip;

import stroom.data.zip.StroomZipEntries.StroomZipEntryGroup;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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
                .withListInputItemType(String.class)
                .withListOutputItemType(String.class)
                .withSingleArgTestFunction(fileNames -> {
                    final StroomZipEntries stroomZipEntries = new StroomZipEntries();
                    for (final String fileName : fileNames) {
                        stroomZipEntries.addFile(fileName);
                    }
                    LOGGER.info("stroomZipEntries: {}", stroomZipEntries);
                    final List<String> baseNames = stroomZipEntries.getBaseNames();
                    final List<String> baseNamesFromGroups = stroomZipEntries.getGroups()
                            .stream().map(StroomZipEntryGroup::getBaseName)
                            .toList();
                    // If this fails then there is a problem in how we track the ordered base names seen
                    assertThat(baseNames)
                            .as("These two lists should match, but neither is the source of truth")
                            .containsExactlyInAnyOrderElementsOf(baseNamesFromGroups);
                    return baseNames;
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
                // Test with different orders as that affects logic
                .addThrowsCase(List.of("001.ctx", "001.foo", "001.bar", "001.meta"), StroomZipNameException.class)
                .addThrowsCase(List.of("001.foo", "001.bar", "001.ctx", "001.meta"), StroomZipNameException.class)
                .addThrowsCase(List.of("001.foo", "001.ctx", "001.bar", "001.meta"), StroomZipNameException.class)
                .addThrowsCase(List.of("001.meta", "001.ctx", "001.foo", "001.bar"), StroomZipNameException.class)
                .addCase(List.of("001.ctx", "001.foo", "001.meta"),
                        List.of("001"))
                .addCase(List.of("001.foo", "001.ctx", "001.meta"),
                        List.of("001"))
                .addCase(List.of("001.dat", "001.dat.ctx", "001.dat.meta"),
                        List.of("001", "001.dat"))
                .build();
    }

    @Test
    void testCloneWithNewBaseName() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001.mydata");
        final StroomZipEntry oldZipEntry = StroomZipEntry.createFromFileName("001.mydata");
        zipEntryGroup.add(oldZipEntry);

        final StroomZipEntryGroup newZipEntryGroup = zipEntryGroup.cloneWithNewBaseName("001");

        final StroomZipEntry newZipEntry = newZipEntryGroup.getByType(StroomZipFileType.DATA).orElseThrow();

        assertThat(newZipEntry.getBaseName())
                .isEqualTo("001");
        assertThat(newZipEntry.getFullName())
                .isEqualTo("001.mydata")
                .isEqualTo(oldZipEntry.getFullName());
        assertThat(newZipEntry.getStroomZipFileType())
                .isEqualTo(StroomZipFileType.DATA)
                .isEqualTo(oldZipEntry.getStroomZipFileType());
    }

    @Test
    void testGetByType() {
        final StroomZipEntries stroomZipEntries = new StroomZipEntries();
        stroomZipEntries.addFile("001");
        stroomZipEntries.addFile("001.ctx");
        stroomZipEntries.addFile("001.meta");

        stroomZipEntries.addFile("002.dat");
        stroomZipEntries.addFile("002.ctx");
        stroomZipEntries.addFile("002.meta");

        assertThat(stroomZipEntries.getByType("001", StroomZipFileType.DATA))
                .hasValue(StroomZipEntry.createFromFileName("001"));
        assertThat(stroomZipEntries.getByType("001", StroomZipFileType.CONTEXT))
                .hasValue(StroomZipEntry.createFromFileName("001.ctx"));
        assertThat(stroomZipEntries.getByType("001", StroomZipFileType.META))
                .hasValue(StroomZipEntry.createFromFileName("001.meta"));
        assertThat(stroomZipEntries.getByType("001", StroomZipFileType.MANIFEST))
                .isEmpty();

        assertThat(stroomZipEntries.getByType("002", StroomZipFileType.DATA))
                .hasValue(StroomZipEntry.createFromFileName("002.dat"));
        assertThat(stroomZipEntries.getByType("002", StroomZipFileType.CONTEXT))
                .hasValue(StroomZipEntry.createFromFileName("002.ctx"));
        assertThat(stroomZipEntries.getByType("002", StroomZipFileType.META))
                .hasValue(StroomZipEntry.createFromFileName("002.meta"));
        assertThat(stroomZipEntries.getByType("002", StroomZipFileType.MANIFEST))
                .isEmpty();

        assertThat(stroomZipEntries.getGroups())
                .hasSize(2);
    }

    // --------------------------------------------------------------------------------


    @Test
    void testStroomZipEntryGroup_Add() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001");
        // base name mismatch
        final StroomZipEntry entryDat = StroomZipEntry.createFromFileName("001");
        zipEntryGroup.add(entryDat);
        final StroomZipEntry entryCtx = StroomZipEntry.createFromFileName("001.ctx");
        zipEntryGroup.add(entryCtx);
        final StroomZipEntry entryMeta = StroomZipEntry.createFromFileName("001.meta");
        zipEntryGroup.add(entryMeta);

        assertThat(zipEntryGroup.getByType(StroomZipFileType.DATA))
                .hasValue(entryDat);
        assertThat(zipEntryGroup.getByType(StroomZipFileType.CONTEXT))
                .hasValue(entryCtx);
        assertThat(zipEntryGroup.getByType(StroomZipFileType.META))
                .hasValue(entryMeta);
    }

    @Test
    void testStroomZipEntryGroup_Add_fail_mismatch() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001");
        // base name mismatch
        Assertions.assertThatThrownBy(() ->
                        zipEntryGroup.add(StroomZipEntry.createFromFileName("002.dat")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testStroomZipEntryGroup_Add_fail_dup() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001");
        zipEntryGroup.add(StroomZipEntry.createFromFileName("001.dat"));
        // base name mismatch
        Assertions.assertThatThrownBy(() ->
                        zipEntryGroup.add(StroomZipEntry.createFromFileName("001.dat")))
                .isInstanceOf(StroomZipNameException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void testStroomZipEntryGroup_HasEntries_true() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001");
        zipEntryGroup.add(StroomZipEntry.createFromFileName("001.dat"));
        assertThat(zipEntryGroup.hasEntries())
                .isTrue();
    }

    @Test
    void testStroomZipEntryGroup_HasEntries_false() {
        final StroomZipEntryGroup zipEntryGroup = new StroomZipEntryGroup("001");
        assertThat(zipEntryGroup.hasEntries())
                .isFalse();
    }
}
