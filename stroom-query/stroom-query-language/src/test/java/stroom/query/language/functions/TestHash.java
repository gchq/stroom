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

package stroom.query.language.functions;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestHash extends AbstractFunctionTest<Hash> {

    @Override
    Class<Hash> getFunctionType() {
        return Hash.class;
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Override
    Stream<TestCase> getTestCases() {
        return Stream.of(
                TestCase.of(
                        "defaultAlgo_noSalt_1",
                        ValString.create("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"),
                        ValString.create("test")),
                TestCase.of(
                        "defaultAlgo_noSalt_2",
                        ValString.create("c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2"),
                        ValString.create("foobar")),
                TestCase.of(
                        "md5_noSalt",
                        ValString.create("098f6bcd4621d373cade4e832627b4f6"),
                        ValString.create("test"),
                        ValString.create("md5")),
                TestCase.of(
                        "sha1_noSalt",
                        ValString.create("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"),
                        ValString.create("test"),
                        ValString.create("sha-1")),
                TestCase.of(
                        "sha256_noSalt",
                        ValString.create("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"),
                        ValString.create("test"),
                        ValString.create("sha-256")),
                TestCase.of(
                        "sha512_noSalt",
                        ValString.create(
                                "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff"),
                        ValString.create("test"),
                        ValString.create("sha-512")),
                TestCase.of(
                        "md5_withSalt",
                        ValString.create("14953be9f11b60a3decd4a40c6eee67a"),
                        ValString.create("test"),
                        ValString.create("md5"),
                        ValString.create("haveSomeSalt")),
                TestCase.of(
                        "sha1_withSalt",
                        ValString.create("154d1a9610558250d1c2907e25b52f12cf1ee1bc"),
                        ValString.create("test"),
                        ValString.create("sha-1"),
                        ValString.create("haveSomeSalt")),
                TestCase.of(
                        "sha256_withSalt",
                        ValString.create("074eb41bbffbd87315fc4095a9f082a646efa1c51e146ebd616e5d47fc3ff9d7"),
                        ValString.create("test"),
                        ValString.create("sha-256"),
                        ValString.create("haveSomeSalt")),
                TestCase.of(
                        "sha512_withSalt",
                        ValString.create(
                                "7559c1cac090b47296f8d8ab5835202821a0024d113b3c4636648d5dce22213ebc3464bb4003890bc763ad2b4c14a5cf4f84241441aefe140d30d8600e5e2520"),
                        ValString.create("test"),
                        ValString.create("sha-512"),
                        ValString.create("haveSomeSalt")),
                TestCase.of(
                        "md5_withSalt",
                        ValString.create(
                                "7559c1cac090b47296f8d8ab5835202821a0024d113b3c4636648d5dce22213ebc3464bb4003890bc763ad2b4c14a5cf4f84241441aefe140d30d8600e5e2520"),
                        ValString.create("test"),
                        ValString.create("sha-512"),
                        ValString.create("haveSomeSalt"))
        );
    }

    /**
     * Just make sure digest resetting works.
     */
    @Test
    void testHash() throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("salt".getBytes(StandardCharsets.UTF_8));
        final String hex1 = HexFormat.of().formatHex(digest.digest("foo".getBytes(StandardCharsets.UTF_8)));

        digest.reset();

        digest.update("salt".getBytes(StandardCharsets.UTF_8));
        final String hex2 = HexFormat.of().formatHex(digest.digest("foo".getBytes(StandardCharsets.UTF_8)));

        assertThat(hex2)
                .isEqualTo(hex1);
    }
}
