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

    @TestFactory
    Stream<DynamicTest> testFromExtension() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(StroomZipFileType.class)
                .withSingleArgTestFunction(StroomZipFileType::fromExtension)
                .withSimpleEqualityAssertion()
                .addCase(null, StroomZipFileType.DATA)
                .addCase("", StroomZipFileType.DATA)
                .addCase(" ", StroomZipFileType.DATA)
                .addCase("foo", StroomZipFileType.DATA)
                .addCase("bar", StroomZipFileType.DATA)
                .addCase("meta", StroomZipFileType.META)
                .addCase("met", StroomZipFileType.META)
                .addCase("hdr", StroomZipFileType.META)
                .addCase("header", StroomZipFileType.META)
                .addCase("ctx", StroomZipFileType.CONTEXT)
                .addCase("context", StroomZipFileType.CONTEXT)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testFromCanonicalExtension() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(StroomZipFileType.class)
                .withSingleArgTestFunction(StroomZipFileType::fromCanonicalExtension)
                .withSimpleEqualityAssertion()
                .addCase(null, null)
                .addCase("", null)
                .addCase(" ", null)
                .addCase("foo", null)
                .addCase("bar", null)
                .addCase("meta", StroomZipFileType.META)
                .addCase("met", null)
                .addCase("hdr", null)
                .addCase("header", null)
                .addCase("ctx", StroomZipFileType.CONTEXT)
                .addCase("context", null)
                .build();
    }
}
