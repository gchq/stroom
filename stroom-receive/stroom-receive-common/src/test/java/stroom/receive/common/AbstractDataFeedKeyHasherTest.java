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

package stroom.receive.common;

import stroom.receive.common.DataFeedKeyHasher.HashOutput;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractDataFeedKeyHasherTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDataFeedKeyHasherTest.class);

    protected void doHashTest(final String input) {
        final DataFeedKeyHasher hasher = getHasher();
        final HashOutput hashOutput = hasher.hash(input);
        LOGGER.info("""
                input: {}
                hash: {}
                salt: {}""", input, hashOutput.hash(), hashOutput.salt());

        final String salt = isSaltEncodedInHash()
                ? null
                : hashOutput.salt();

        assertThat(hasher.verify(input, hashOutput.hash(), salt))
                .isEqualTo(true);
    }

    abstract DataFeedKeyHasher getHasher();

    abstract boolean isSaltEncodedInHash();
}
