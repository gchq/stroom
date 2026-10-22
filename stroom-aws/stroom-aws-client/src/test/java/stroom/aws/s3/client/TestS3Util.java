/*
 * Copyright 2026 Crown Copyright
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

package stroom.aws.s3.client;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestS3Util {

    @TestFactory
    Stream<DynamicTest> testCleanKeyName() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withSingleArgTestFunction(S3Util::cleanKeyName)
                .withSimpleEqualityAssertion()
                .addCase("", "")
                .addCase("RAW_EVENTS/2026/10/22/001/MY_FEED/0000001.zip",
                        "RAW_EVENTS/2026/10/22/001/MY_FEED/0000001.zip")
                // The safe special chars are kept
                .addCase("a!b-c_d.e*f'g(h)i/j", "a!b-c_d.e*f'g(h)i/j")
                // Everything in the old accidental '!-_' range that isn't a safe char is replaced
                .addCase("a\"b#c$d%e&f+g,h:i;j<k=l>m?n@o[p\\q]r^s",
                        "a-b-c-d-e-f-g-h-i-j-k-l-m-n-o-p-q-r-s")
                .addCase("Raw Events/feed", "Raw-Events/feed")
                .addCase("a{b}c|d~e`f", "a-b-c-d-e-f")
                .addCase("/a//b///c/", "a/b/c")
                .build();
    }
}
