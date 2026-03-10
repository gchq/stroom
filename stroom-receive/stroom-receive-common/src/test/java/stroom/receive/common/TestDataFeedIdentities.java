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

import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.DataFeedKeyHasher.HashOutput;
import stroom.test.common.TestUtil;
import stroom.util.cert.DNFormat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

class TestDataFeedIdentities {

    @Test
    void testSerde() {
        final DataFeedKeyHasher hasher = new Argon2DataFeedKeyHasher();

        @SuppressWarnings("checkstyle:lineLength") final String key1 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "okfXqkmtns3k4828fZcnutWUFmegj3hqk83o9sYCLefWGTrRrpT6Bt23FuT1ebwcftPNaL1B7aFbK37gbpefZgQeeP3esbnvNXu612co4awVxpn33He6i1vn7g8kUFEk";
        @SuppressWarnings("checkstyle:lineLength") final String key2 =
                "sdk_"
                + hasher.getAlgorithm().getUniqueId()
                + "_"
                + "7GqxzCAhBnui4wSCicVtFdmghBxtBAQVDbLrsqDAqthuoHTmVEorJf6xvWviWajwKboJUDvanQXK8UpYroqwfxxYhsG264acXbjcpeQPutNqXrq3rTNqWWYNWaQrj2e1";

        final HashOutput hashOutput1 = hasher.hash(key1);
        final HashedDataFeedKey hashedDataFeedKey1 = new HashedDataFeedKey(
                hashOutput1.hash(),
                hashOutput1.salt(),
                hasher.getAlgorithm(),
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "system 1",
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        final HashOutput hashOutput2 = hasher.hash(key2);
        final HashedDataFeedKey hashedDataFeedKey2 = new HashedDataFeedKey(
                hashOutput2.hash(),
                hashOutput2.salt(),
                hasher.getAlgorithm(),
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "system 2",
                        "key3", "val3",
                        "key4", "val4"),
                Instant.now().plus(2, ChronoUnit.DAYS).toEpochMilli());
//        final List<HashedDataFeedKey> hashedDataFeedKeyList = List.of(hashedDataFeedKey1, hashedDataFeedKey2);

        final CertificateIdentity certificateIdentity1 = new CertificateIdentity(
                "/DC=com/DC=example/DC=corp/OU=Users/CN=John Doe/emailAddress=john_doe@example.com",
                DNFormat.LDAP,
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "system 3",
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(3, ChronoUnit.DAYS).toEpochMilli());
        final CertificateIdentity certificateIdentity2 = new CertificateIdentity(
                "/DC=com/DC=example/DC=corp/OU=Users/CN=Jane Doe/emailAddress=jane_doe@example.com",
                DNFormat.LDAP,
                Map.of(
                        StandardHeaderArguments.ACCOUNT_ID, "system 4",
                        "key1", "val1",
                        "key2", "val2"),
                Instant.now().plus(4, ChronoUnit.DAYS).toEpochMilli());
//        final List<CertificateIdentity> certificateIdentityList = List.of(certificateIdentity1, certificateIdentity2);
        final List<DataFeedIdentity> dataFeedIdentityList = List.of(
                hashedDataFeedKey1,
                certificateIdentity1,
                hashedDataFeedKey2,
                certificateIdentity2);

        final DataFeedIdentities dataFeedIdentities = new DataFeedIdentities(dataFeedIdentityList);
        TestUtil.testSerialisation(dataFeedIdentities, DataFeedIdentities.class);
    }
}
