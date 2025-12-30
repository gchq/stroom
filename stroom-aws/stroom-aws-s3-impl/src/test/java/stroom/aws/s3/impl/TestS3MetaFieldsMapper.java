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

package stroom.aws.s3.impl;

import stroom.test.common.TestUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestS3MetaFieldsMapper {

    @TestFactory
    Stream<DynamicTest> testGetS3Key() {
        final S3MetaFieldsMapper s3MetaFieldsMapper = new S3MetaFieldsMapper();

        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String s3Key = s3MetaFieldsMapper.getS3Key(testCase.getInput())
                            .orElse(null);

                    if (testCase.getExpectedOutput() != null) {
                        final String original = s3MetaFieldsMapper.getOriginalKey(s3Key)
                                .orElse(null);

                        Assertions.assertThat(original)
                                .isEqualTo(testCase.getInput());
                    }
                    return s3Key;
                })
                .withSimpleEqualityAssertion()
                .addCase("File Size", "file size")
                .addCase("File&$Size", null)
                .build();
    }
}
