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

import org.junit.jupiter.api.Test;

class TestArgon2DataFeedKeyHasher extends AbstractDataFeedKeyHasherTest {

    @Override
    boolean isSaltEncodedInHash() {
        return false;
    }

    @Override
    DataFeedKeyHasher getHasher() {
        return new Argon2DataFeedKeyHasher();
    }

    @Test
    void test1() {
        doHashTest("hello world");
    }

    @Test
    void test2() {
        final String input = "sdk_L8K8ttQc9Y7QtdrTX7PpYTXvDT5JJ9ZzR9pkTVHrAHbUaZXrDwmGMQfFW74o59L5dGSs5nnH" +
                             "unA8WboQ8Lbv4YENAYYM64p3P9zcZ5Xa5nfh8trqiyLWaFQ2gdQPinBL";
        doHashTest(input);
    }
}
