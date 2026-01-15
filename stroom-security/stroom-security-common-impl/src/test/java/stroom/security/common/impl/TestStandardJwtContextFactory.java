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

package stroom.security.common.impl;

import stroom.security.common.impl.StandardJwtContextFactory.JwsParts;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.OpenId;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.string.TemplateUtil;

import com.google.inject.TypeLiteral;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Stream;

class TestStandardJwtContextFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStandardJwtContextFactory.class);

    @TestFactory
    Stream<DynamicTest> testAwsPublicKeyUriFromSigner() {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<Tuple2<String, Set<String>>>() {
                })
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final String signer = testCase.getInput()._1;
                    final Set<String> expectedSignerPrefixes = testCase.getInput()._2;
                    final String json = LogUtil.message("""
                            {
                              "signer": "{}",
                              "kid": "999"
                            }""", signer);

                    final String header = Base64.getEncoder()
                            .encodeToString(json.getBytes(StandardCharsets.UTF_8));

                    final JwsParts jwsParts = new JwsParts(
                            null,
                            header,
                            null,
                            null);

                    return getAwsPublicKeyUri(jwsParts, expectedSignerPrefixes);
                })
                .withSimpleEqualityAssertion()

                .addNamedCase("Single, full",
                        Tuple.of("arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                                Set.of("arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678")),
                        "https://public-keys.auth.elb.region-x.amazonaws.com/999")

                .addNamedCase("Single, partial",
                        Tuple.of("arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                                Set.of("arn:aws:elasticloadbalancing:region-x:1234:")),
                        "https://public-keys.auth.elb.region-x.amazonaws.com/999")

                .addNamedCase("Multiple, full",
                        Tuple.of("arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678",
                                Set.of(
                                        "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                                        "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678")),
                        "https://public-keys.auth.elb.region-y.amazonaws.com/999")

                .addNamedCase("Multiple, partial",
                        Tuple.of("arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678",
                                Set.of(
                                        "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp",
                                        "arn:aws:elasticloadbalancing:region-y:1234:")),
                        "https://public-keys.auth.elb.region-y.amazonaws.com/999")

                .build();
    }

    private String getAwsPublicKeyUri(final JwsParts jwsParts, final Set<String> expectedSignerPrefixes) {
        return StandardJwtContextFactory.getAwsPublicKeyUri(
                jwsParts,
                expectedSignerPrefixes,
                TemplateUtil.parseTemplate(AbstractOpenIdConfig.DEFAULT_AWS_PUBLIC_KEY_URI_TEMPLATE));
    }

    @Test
    void getAwsPublicKeyUriFromSigner_blankSigner() {
        final String signer2 = "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678";

        final String json = """
                {
                  "signer": "",
                  "kid": "999"
                }""";

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer2));
                        })
                .hasMessageContaining("does not match")
                .hasMessageContaining(AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_badSigner() {
        final String signer1 = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";
        final String signer2 = "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer2));
                        })
                .hasMessageContaining("does not match")
                .hasMessageContaining(AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_badRegionInSigner() {
        final String signer1 = "arn:aws:elasticloadbalancing:region-x/foo:1234:loadbalancer/app/MyApp/5678";
        final String signer2 = "arn:aws:elasticloadbalancing:region-y:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer1, signer2));
                        })
                .hasMessageContaining("AWS region")
                .hasMessageContaining("does not match pattern")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_noRegionInSigner() {
        final String signer1 = "arn:aws:elasticloadbalancing::1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer1));
                        })
                .hasMessageContaining("AWS region")
                .hasMessageContaining("does not match pattern")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_nullExpectedSigners() {
        final String signer1 = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}",
                  "kid": "999"
                }""", signer1);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, null);
                        })
                .hasMessageContaining("does not match")
                .hasMessageContaining(AbstractOpenIdConfig.PROP_NAME_EXPECTED_SIGNER_PREFIXES)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_signerNotFound() {
        final String signer = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = """
                {
                  "kid": "999"
                }""";

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer));
                        })
                .hasMessageContaining("Missing")
                .hasMessageContaining(StandardJwtContextFactory.SIGNER_HEADER_KEY)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getAwsPublicKeyUriFromSigner_noKeyId() {
        final String signer = "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678";

        final String json = LogUtil.message("""
                {
                  "signer": "{}"
                }""", signer);

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        final JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        Assertions.assertThatThrownBy(
                        () -> {
                            getAwsPublicKeyUri(jwsParts, Set.of(signer));
                        })
                .hasMessageContaining("Missing")
                .hasMessageContaining(OpenId.KEY_ID)
                .isInstanceOf(RuntimeException.class);
    }
}
