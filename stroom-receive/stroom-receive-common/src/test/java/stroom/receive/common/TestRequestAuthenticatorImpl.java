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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

class TestRequestAuthenticatorImpl {

    private final UserIdentityFactory mockUserIdentityFactory = Mockito.mock(UserIdentityFactory.class);
    private final ReceiveDataConfig configTokenOnly = ReceiveDataConfig.builder()
            .withAuthenticationRequired(true)
            .withEnabledAuthenticationTypes(AuthenticationType.TOKEN)
            .build();
    private final ReceiveDataConfig configCertOnly = ReceiveDataConfig.builder()
            .withAuthenticationRequired(true)
            .withEnabledAuthenticationTypes(AuthenticationType.CERTIFICATE)
            .build();
    private final ReceiveDataConfig dataFeedKeyOnly = ReceiveDataConfig.builder()
            .withAuthenticationRequired(true)
            .withEnabledAuthenticationTypes(AuthenticationType.DATA_FEED_KEY)
            .build();
    private final ReceiveDataConfig configTokenAndCert = ReceiveDataConfig.builder()
            .withAuthenticationRequired(true)
            .withEnabledAuthenticationTypes(AuthenticationType.TOKEN, AuthenticationType.CERTIFICATE)
            .build();
    private final ReceiveDataConfig configAllTypes = ReceiveDataConfig.builder()
            .withAuthenticationRequired(true)
            .withEnabledAuthenticationTypes(AuthenticationType.values())
            .build();
    private final ReceiveDataConfig configOptionalAuth = ReceiveDataConfig.builder()
            .withAuthenticationRequired(false)
            .withEnabledAuthenticationTypes(AuthenticationType.values())
            .build();

    private AttributeMap attributeMap = new AttributeMap();

    private void setupAttributeMap() {
        attributeMap = new AttributeMap();
        attributeMap.put(HttpHeaders.AUTHORIZATION, "foo");
    }

    @TestFactory
    Stream<DynamicTest> authenticate_tokenEnabled() {
        final String certCn = "My CN";
        final UserIdentity tokenUser = new TestUserIdentity("1"); // We are mocking so type doesn't matter here
        final UserIdentity certUser = new CertificateUserIdentity(certCn); // Type matters here

        final HashedDataFeedKey hashedDataFeedKey = new HashedDataFeedKey(
                "my hash",
                "my salt",
                DataFeedKeyHashAlgorithm.ARGON2,
                Map.of(StandardHeaderArguments.ACCOUNT_ID, "MyAccountId"),
                Long.MAX_VALUE);
        final UserIdentity dataFeedKeyUser = new DataFeedKeyUserIdentity("MyAccountId");

        // Type matters here
        final UnauthenticatedUserIdentity unauthUser = UnauthenticatedUserIdentity.getInstance();

        return TestUtil.buildDynamicTestStream()
                .withInputType(Input.class)
                .withOutputTypes(UserIdentity.class, StroomStatusCode.class)
                .withTestFunction(testCase -> {
                    final HttpServletRequest mockHttpServletRequest = Mockito.mock(HttpServletRequest.class);
                    final DataFeedKeyService mockDataFeedKeyService = Mockito.mock(DataFeedKeyService.class);
                    final OidcTokenAuthenticator mockOidcTokenAuthenticator = Mockito.mock(
                            OidcTokenAuthenticator.class);
                    final CertificateAuthenticator mockCertificateAuthenticator = Mockito.mock(
                            CertificateAuthenticator.class);
                    final AllowUnauthenticatedAuthenticator mockAllowUnauthenticatedAuthenticator =
                            Mockito.mock(AllowUnauthenticatedAuthenticator.class);
                    final RequestAuthenticator requestAuthenticator = new RequestAuthenticatorImpl(
                            mockUserIdentityFactory,
                            () -> testCase.getInput().receiveDataConfig,
                            () -> mockDataFeedKeyService,
                            () -> mockOidcTokenAuthenticator,
                            () -> mockCertificateAuthenticator,
                            () -> mockAllowUnauthenticatedAuthenticator);
                    setupAttributeMap();
                    final Set<AuthMethod> authMethodsInReq = testCase.getInput().authMethodsInReq;
                    final ReceiveDataConfig receiveDataConfig = testCase.getInput().receiveDataConfig;
                    final Optional<UserIdentity> userFromTokenAuth = testCase.getInput().userFromTokenAuth;
                    final StroomStatusCode errorCode = testCase.getInput().errorCode;

                    if (receiveDataConfig.isAuthenticationTypeEnabled(AuthenticationType.TOKEN)) {
                        if (errorCode != null) {
                            Mockito.when(mockOidcTokenAuthenticator.authenticate(Mockito.any(), Mockito.any()))
                                    .thenThrow(new StroomStreamException(errorCode, null));
                        } else {
                            Mockito.when(mockOidcTokenAuthenticator.authenticate(Mockito.any(), Mockito.any()))
                                    .thenReturn(authMethodsInReq.contains(AuthMethod.TOKEN)
                                            ? userFromTokenAuth
                                            : Optional.empty());
                        }
                    }
                    if (receiveDataConfig.isAuthenticationTypeEnabled(AuthenticationType.CERTIFICATE)) {
                        if (errorCode != null) {
                            Mockito.when(mockCertificateAuthenticator.authenticate(Mockito.any(), Mockito.any()))
                                    .thenThrow(new StroomStreamException(errorCode, null));
                        } else {
                            Mockito.when(mockCertificateAuthenticator.authenticate(Mockito.any(), Mockito.any()))
                                    .thenReturn(authMethodsInReq.contains(AuthMethod.CERT)
                                            ? Optional.of(certUser)
                                            : Optional.empty());
                        }
                    }
                    if (receiveDataConfig.isAuthenticationTypeEnabled(AuthenticationType.DATA_FEED_KEY)) {
                        if (errorCode != null) {
                            Mockito.when(mockDataFeedKeyService.authenticate(Mockito.any(), Mockito.any()))
                                    .thenThrow(new StroomStreamException(errorCode, null));
                        } else {
                            Mockito.when(mockDataFeedKeyService.authenticate(Mockito.any(), Mockito.any()))
                                    .thenReturn(authMethodsInReq.contains(AuthMethod.DATA_FEED_KEY)
                                            ? Optional.of(dataFeedKeyUser)
                                            : Optional.empty());
                        }
                    }

                    if (!receiveDataConfig.isAuthenticationRequired()) {
                        Mockito.when(mockAllowUnauthenticatedAuthenticator.authenticate(Mockito.any(), Mockito.any()))
                                .thenReturn(Optional.of(unauthUser));
                    }
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
                    } catch (final StroomStreamException e) {
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
                                .doesNotContain(HttpHeaders.AUTHORIZATION);
                        Assertions.assertThat(attributeMap.get(StandardHeaderArguments.UPLOAD_USER_ID))
                                .isEqualTo(testOutcome.getActualOutput()._1.subjectId());
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
                                EnumSet.of(AuthMethod.TOKEN),
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "token only - No token",
                        new Input(
                                configTokenOnly,
                                EnumSet.noneOf(AuthMethod.class),
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_REQUIRED))
                .addNamedCase(
                        "token only - Token found but not authenticated",
                        new Input(
                                configTokenOnly,
                                EnumSet.of(AuthMethod.TOKEN),
                                Optional.empty(),
                                StroomStatusCode.CLIENT_TOKEN_NOT_AUTHENTICATED),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_NOT_AUTHENTICATED))
                .addNamedCase(
                        "token only - Token not found, cert found but ignored",
                        new Input(
                                configTokenOnly,
                                EnumSet.of(AuthMethod.CERT),
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_REQUIRED))
                .addNamedCase(
                        "Cert only - Valid cert",
                        new Input(
                                configCertOnly,
                                EnumSet.of(AuthMethod.CERT),
                                Optional.empty()),
                        Tuple.of(certUser, null))
                .addNamedCase(
                        "Cert only - no cert",
                        new Input(
                                configCertOnly,
                                EnumSet.noneOf(AuthMethod.class),
                                Optional.empty(),
                                StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED),
                        Tuple.of(null, StroomStatusCode.CLIENT_CERTIFICATE_REQUIRED))
                .addNamedCase(
                        "Token & Cert - valid token",
                        new Input(
                                configTokenAndCert,
                                EnumSet.of(AuthMethod.TOKEN),
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "Token & Cert - valid cert",
                        new Input(
                                configTokenAndCert,
                                EnumSet.of(AuthMethod.CERT),
                                Optional.empty()),
                        Tuple.of(certUser, null))
                .addNamedCase(
                        "Token & Cert - valid token + valid cert",
                        new Input(
                                configTokenAndCert,
                                EnumSet.of(AuthMethod.TOKEN, AuthMethod.CERT),
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "Token & Cert - neither present",
                        new Input(
                                configTokenAndCert,
                                EnumSet.noneOf(AuthMethod.class),
                                Optional.empty()),
                        Tuple.of(null, StroomStatusCode.CLIENT_TOKEN_OR_CERT_REQUIRED))
                .addNamedCase(
                        "No auth required - neither present",
                        new Input(
                                configOptionalAuth,
                                EnumSet.noneOf(AuthMethod.class),
                                Optional.empty()),
                        Tuple.of(unauthUser, null))
                .addNamedCase(
                        "No auth required - Valid token",
                        new Input(
                                configOptionalAuth,
                                EnumSet.of(AuthMethod.TOKEN),
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "No auth required - Valid cert",
                        new Input(
                                configOptionalAuth,
                                EnumSet.of(AuthMethod.CERT),
                                Optional.empty()),
                        Tuple.of(certUser, null))
                .addNamedCase(
                        "No auth required - both present, token used in pref",
                        new Input(
                                configOptionalAuth,
                                EnumSet.of(AuthMethod.TOKEN, AuthMethod.CERT),
                                Optional.of(tokenUser)),
                        Tuple.of(tokenUser, null))
                .addNamedCase(
                        "No auth required - all present, DFK used in pref",
                        new Input(
                                configOptionalAuth,
                                EnumSet.of(AuthMethod.DATA_FEED_KEY, AuthMethod.TOKEN, AuthMethod.CERT),
                                Optional.of(dataFeedKeyUser)),
                        Tuple.of(dataFeedKeyUser, null))
                .build();
    }


    // --------------------------------------------------------------------------------


    private record Input(
            ReceiveDataConfig receiveDataConfig,
            Set<AuthMethod> authMethodsInReq,
            Optional<UserIdentity> userFromTokenAuth,
            StroomStatusCode errorCode) {

        private Input(final ReceiveDataConfig receiveDataConfig,
                      final Set<AuthMethod> authMethodsInReq,
                      final Optional<UserIdentity> userFromTokenAuth) {
            this(receiveDataConfig, authMethodsInReq, userFromTokenAuth, null);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class TestUserIdentity implements UserIdentity {

        private final String id;

        public TestUserIdentity(final String id) {
            this.id = id;
        }

        @Override
        public String subjectId() {
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

    private enum AuthMethod {
        DATA_FEED_KEY,
        TOKEN,
        CERT,
        ;
    }
}
