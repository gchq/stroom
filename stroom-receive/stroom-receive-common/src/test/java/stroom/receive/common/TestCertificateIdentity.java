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

package stroom.receive.common;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

class TestCertificateIdentity {

    @Test
    void testSerde() {
        final CertificateIdentity certificateIdentity = new CertificateIdentity(
                "my-DN",
                Map.of(
                        "foo", "bar",
                        "FOO", "BAR", // Dup key if treated as case-insensitive
                        "cat", "felix",
                        "dog", "fido"),
                Instant.now().toEpochMilli());
        TestUtil.testSerialisation(certificateIdentity, CertificateIdentity.class);
    }
}
