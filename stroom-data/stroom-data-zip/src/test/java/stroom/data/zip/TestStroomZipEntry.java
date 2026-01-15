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

import stroom.test.common.TestUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestStroomZipEntry {

    @TestFactory
    Stream<DynamicTest> testCreateFromFileName() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(ZipEntryParts.class)
                .withSingleArgTestFunction(fileName -> {
                    final StroomZipEntry zipEntry = StroomZipEntry.createFromFileName(fileName);
                    return new ZipEntryParts(zipEntry.getBaseName(),
                            zipEntry.getFullName(),
                            zipEntry.getStroomZipFileType());
                })
                .withSimpleEqualityAssertion()
                .addCase("001",
                        new ZipEntryParts("001", "001", StroomZipFileType.DATA))
                .addCase("001.unknown",
                        new ZipEntryParts("001.unknown", "001.unknown", StroomZipFileType.DATA))
                .addCase("001.ctx",
                        new ZipEntryParts("001", "001.ctx", StroomZipFileType.CONTEXT))
                // Unknown extension so it is part of basename
                .addCase("abc.001",
                        new ZipEntryParts("abc.001", "abc.001", StroomZipFileType.DATA))
                // Unknown extension so it is part of basename
                .addCase("abc.001.unknown",
                        new ZipEntryParts(
                                "abc.001.unknown", "abc.001.unknown", StroomZipFileType.DATA))
                .addCase("abc.001.ctx",
                        new ZipEntryParts("abc.001", "abc.001.ctx", StroomZipFileType.CONTEXT))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testCreateFromBaseName() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, StroomZipFileType>>() {
                })
                .withOutputType(ZipEntryParts.class)
                .withSingleArgTestFunction(baseNameAndType -> {
                    final StroomZipEntry zipEntry = StroomZipEntry.createFromBaseName(
                            baseNameAndType._1, baseNameAndType._2);
                    return new ZipEntryParts(zipEntry.getBaseName(),
                            zipEntry.getFullName(),
                            zipEntry.getStroomZipFileType());
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("001", StroomZipFileType.DATA),
                        new ZipEntryParts("001", "001.dat", StroomZipFileType.DATA))
                .addCase(Tuple.of("abc.001", StroomZipFileType.DATA),
                        new ZipEntryParts("abc.001", "abc.001.dat", StroomZipFileType.DATA))
                .addCase(Tuple.of("abc.001", StroomZipFileType.CONTEXT),
                        new ZipEntryParts("abc.001", "abc.001.ctx", StroomZipFileType.CONTEXT))
                .build();
    }


    // --------------------------------------------------------------------------------


    private record ZipEntryParts(
            String baseName,
            String fullName,
            StroomZipFileType fileType) {

        @Override
        public String toString() {
            return String.join("|",
                    baseName,
                    fullName,
                    NullSafe.toString(fileType, StroomZipFileType::getExtension));
        }
    }
}
