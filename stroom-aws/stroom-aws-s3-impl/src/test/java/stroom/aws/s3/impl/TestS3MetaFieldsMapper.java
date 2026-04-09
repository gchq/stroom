/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestS3MetaFieldsMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestS3MetaFieldsMapper.class);

    @TestFactory
    Stream<DynamicTest> testGetS3Key() {
        final S3MetaFieldsMapper s3MetaFieldsMapper = new S3MetaFieldsMapper();

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Tuple2<String, String>>() {
                })
                .withTestFunction(testCase -> {
                    final String input = testCase.getInput();
                    final String s3Key = s3MetaFieldsMapper.getS3Key(input)
                            .orElse(null);

                    if (testCase.getExpectedOutput() != null) {
                        final String original = s3MetaFieldsMapper.getOriginalKey(s3Key)
                                .orElse(null);
                        LOGGER.debug("input: {}, s3Key: {}, original: {}", input, s3Key, original);
                        return Tuple.of(s3Key, original);
                    } else {
                        LOGGER.debug("input: {}, s3Key: {}", input, s3Key);
                        return Tuple.of(s3Key, null);
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase("Feed", Tuple.of("feed", "Feed"))
                .addCase("feed", Tuple.of("feed", "Feed"))
                .addCase("FEED", Tuple.of("feed", "Feed"))
                .addCase("Type", Tuple.of("type", "Type"))
                .addCase("file Size", Tuple.of("file-size", "File Size"))
                .addCase("Raw Size", Tuple.of("raw-size", "Raw Size"))
                .addCase("File&$Size", Tuple.of(null, null))
                .addCase("foo", Tuple.of(null, null))
                .build();
    }
}
