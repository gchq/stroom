/*
 * Copyright 2024 Crown Copyright
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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.test.common.TestUtil;
import stroom.util.cert.CertificateExtractor;
import stroom.util.shared.string.CIKeys;

import io.vavr.Tuple;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

class TestRequestAuthenticatorImpl {

    private final UserIdentityFactory mockUserIdentityFactory = Mockito.mock(UserIdentityFactory.class);
    private final ReceiveDataConfig configTokenOnly = new ReceiveDataConfig()
            .withAuthenticationRequired(true)
            .withTokenAuthenticationEnabled(true)
            .withCertificateAuthenticationEnabled(false);
    private final ReceiveDataConfig configCertOnly = new ReceiveDataConfig()
            .withAuthenticationRequired(true)
            .withTokenAuthenticationEnabled(false)
            .withCertificateAuthenticationEnabled(true);
    private final ReceiveDataConfig configTokenAndCert = new ReceiveDataConfig()
            .withAuthenticationRequired(true)
            .withTokenAuthenticationEnabled(true)
            .withCertificateAuthenticationEnabled(true);
    private final ReceiveDataConfig configOptionalAuth = new ReceiveDataConfig()
            .withAuthenticationRequired(false)
            .withTokenAuthenticationEnabled(true)
            .withCertificateAuthenticationEnabled(true);

    private AttributeMap attributeMap = new AttributeMap();

    private void setupAttributeMap() {
        attributeMap = new AttributeMap();
        attributeMap.put(HttpHeaders.AUTHORIZATION, "foo");
    }

    @TestFactory
    Stream<DynamicTest> authenticate_tokenEnabled() {

        final HttpServletRequest mockHttpServletRequest = Mockito.mock(HttpServletRequest.class);
        final CertificateExtractor mockCertificateExtractor = Mockito.mock(CertificateExtractor.class);

        final String certCn = "My CN";
        final UserIdentity tokenUser = new TestUserIdentity("1"); // We are mocking so type doesn't matter here
        final UserIdentity certUser = new CertificateUserIdentity(certCn); // Type matters here
        final UnauthenticatedUserIdentity unauthUser = UnauthenticatedUserIdentity.getInstance();

        return TestUtil.buildDynamicTestStream()
                .withInputType(Input.class)
                .withOutputTypes(UserIdentity.class, StroomStatusCode.class)
                .withTestFunction(testCase -> {
                    final RequestAuthenticator requestAuthenticator = new RequestAuthenticatorImpl(
                            mockUserIdentityFactory,
                            () -> testCase.getInput().receiveDataConfig,
                            mockCertificateExtractor);
                    setupAttributeMap();
                    Mockito.when(mockUserIdentityFactory.hasAuthenticationToken(Mockito.any()))
                            .thenReturn(testCase.getInput().tokenState.asBoolean());
                    Mockito.when(mockUserIdentityFactory.getApiUserIdentity(Mockito.any()))
                            .thenReturn(testCase.getInput().userFromTokenAuth);
                    Mockito.when(mockCertificateExtractor.getCN(Mockito.any()))
                            .thenReturn(testCase.getInput().certState.asBoolean()
                                    ? Optional.of(certCn)
                                    : Optional.empty());
                    Mockito.doAnswer(invocation -> {
                        attributeMap.remove(HttpHeaders.AUTHORIZATION);
                        return null;
                    }).when(mockUserIdentityFactory).removeAuthEntries(Mockito.any());

                    UserIdentity outputUserIdentity = null;
                    StroomStatusCode stroomStatusCode = null;

                    Assertions.assertThat(attributeMap.size())
                            .isEqualTo(1);

                    try {
                        outputUserIdentity = requestAuthenticator.authenticate(
                                mockHttpServletRequest,
                                attributeMap);
                    } catch (StroomStreamException e) {
                        stroomStatusCode = e.getStroomStreamStatus().getStroomStatusCode();
                    }
                    return Tuple.of(outputUserIdentity, stroomStatusCode);
                })
                .withAssertions(testOutcome -> {
                    Assertions.assertThat(testOutcome.getActualOutput())
                            .isEqualTo(testOutcome.getExpectedOutput());
                    final StroomStatusCode statusCode = testOutcome.getActualOutput()._2;
                    if (statusCode == null) {
                        // No ex thrown
                        Assertions.assertThat(attributeMap.size())
                                .isEqualTo(2); // Two new items added
                        Assertions.assertThat(attributeMap.keySet())
                                .doesNotContain(CIKeys.AUTHORIZATION);
                        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.UPLOAD_USER_ID))
                                .isEqualTo(testOutcome.getActualOutput()._1.getSubjectId());
                    } else {
                        // Ex thrown
                        Assertions.assertThat(attributeMap.size())
                                .isEqualTo(1); // initial contents
                    }
                })
                .addNamedCase(
                        "token only - Valid token",
                        new Input(
                                configTokenOnly,
                                TokenState.FOUND,
                                CertState.NOT_FOUND,
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "token only - No token",
                        new Input(
                                configTokenOnly,
                                TokenState.NOT_FOUND,
                                CertState.NOT_FOUND,
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_REQUIRED))
                .addNamedCase(
                        "token only - Token found but not authenticated",
                        new Input(
                                configTokenOnly,
                                TokenState.FOUND,
                                CertState.NOT_FOUND,
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_NOT_AUTHENTICATED))
                .addNamedCase(
                        "token only - Token not found, cert found but ignored",
                        new Input(
                                configTokenOnly,
                                TokenState.NOT_FOUND,
                                CertState.FOUND,
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_REQUIRED))
                .addNamedCase(
                        "Cert only - Valid cert",
                        new Input(
                                configCertOnly,
                                TokenState.NOT_FOUND,
                                CertState.FOUND,
                                Optional.empty()),
                        Tuple.of(certUser, null))
                .addNamedCase(
                        "Cert only - no cert",
                        new Input(
                                configCertOnly,
                                TokenState.NOT_FOUND,
                                CertState.NOT_FOUND,
                                Optional.empty()), // ignored
                        Tuple.of(null, StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED))
                .addNamedCase(
                        "Token & Cert - valid token",
                        new Input(
                                configTokenAndCert,
                                TokenState.FOUND,
                                CertState.NOT_FOUND,
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "Token & Cert - valid cert",
                        new Input(
                                configTokenAndCert,
                                TokenState.NOT_FOUND,
                                CertState.FOUND,
                                Optional.empty()),
                        Tuple.of(certUser, null))
                .addNamedCase(
                        "Token & Cert - valid token + valid cert",
                        new Input(
                                configTokenAndCert,
                                TokenState.FOUND,
                                CertState.FOUND,
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "Token & Cert - neither present",
                        new Input(
                                configTokenAndCert,
                                TokenState.NOT_FOUND,
                                CertState.NOT_FOUND,
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_OR_CERT_REQUIRED))
                .addNamedCase(
                        "No auth required - neither present",
                        new Input(
                                configOptionalAuth,
                                TokenState.NOT_FOUND,
                                CertState.NOT_FOUND,
                                Optional.empty()),
                        Tuple.of(unauthUser, null))
                .addNamedCase(
                        "No auth required - Valid token",
                        new Input(
                                configOptionalAuth,
                                TokenState.FOUND,
                                CertState.NOT_FOUND,
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "No auth required - Valid cert",
                        new Input(
                                configOptionalAuth,
                                TokenState.NOT_FOUND,
                                CertState.FOUND,
                                Optional.empty()),
                        Tuple.of(certUser, null))
                .addNamedCase(
                        "No auth required - both present, token used in pref",
                        new Input(
                                configOptionalAuth,
                                TokenState.FOUND,
                                CertState.FOUND,
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .build();
    }


    // --------------------------------------------------------------------------------


    private record Input(
            ReceiveDataConfig receiveDataConfig,
            TokenState tokenState,
            CertState certState,
            Optional<UserIdentity> userFromTokenAuth) {

    }


    // --------------------------------------------------------------------------------


    private static final class TestUserIdentity implements UserIdentity {

        private final String id;

        public TestUserIdentity(final String id) {
            this.id = id;
        }

        @Override
        public String getSubjectId() {
            return id;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final TestUserIdentity that = (TestUserIdentity) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "TestUserIdentity{" +
                    "id='" + id + '\'' +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


    // Make tests easier to read
    private enum TokenState {
        FOUND(true),
        NOT_FOUND(false);

        private final boolean isTokenFound;

        TokenState(final boolean isTokenFound) {
            this.isTokenFound = isTokenFound;
        }

        boolean asBoolean() {
            return isTokenFound;
        }
    }


    // --------------------------------------------------------------------------------


    // Make tests easier to read
    private enum CertState {
        FOUND(true),
        NOT_FOUND(false);

        private final boolean isCertFound;

        CertState(final boolean isCertFound) {
            this.isCertFound = isCertFound;
        }

        boolean asBoolean() {
            return isCertFound;
        }
    }
}
