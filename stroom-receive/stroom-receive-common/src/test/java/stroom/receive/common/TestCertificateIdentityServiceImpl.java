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

import stroom.meta.api.AttributeMap;
import stroom.security.api.UserIdentity;
import stroom.util.cert.CertificateExtractor;

import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CertificateIdentityServiceImplTest {

    private static final String OWNER = "Account1234";
    private static final String OTHER_OWNER = "Account6789";
    private static final String OWNER_META_KEY = ReceiveDataConfig.DEFAULT_OWNER_META_KEY;
    private static final String TEST_DN = "CN=test-certificate-dn";
    private static final String OTHER_TEST_DN = "CN=other-test-certificate-dn";

    @Mock
    private CertificateExtractor certificateExtractor;
    @Mock
    private Provider<ReceiveDataConfig> receiveDataConfigProvider;
    @Mock
    private ReceiveDataConfig receiveDataConfig;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private CertificateIdentityServiceImpl certificateIdentityService;

    @BeforeEach
    void setUp() {
        Mockito.when(receiveDataConfigProvider.get())
                .thenReturn(receiveDataConfig);
        Mockito.when(receiveDataConfig.getDataFeedOwnerMetaKey())
                .thenReturn(OWNER_META_KEY);
    }

    @Test
    void authenticate_noHeadersProvided() {
        final AttributeMap attributeMap = new AttributeMap();

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);

        assertThat(result)
                .isEmpty();
        // Since keyOwner is missing, it should exit early without checking the certificate DN
        Mockito.verify(certificateExtractor, Mockito.never())
                .getDN(request);
    }

    @Test
    void authenticate_hasDnButNoKeyOwnerHeader() {
        // The certificate is present in the request...
        // Note: The method exits early if keyOwner is blank, so we don't strictly need to mock getDN here,
        // but we'll simulate the environment having a DN but missing the specific header.
        final AttributeMap attributeMap = new AttributeMap();

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);

        assertThat(result).isEmpty();
        Mockito.verify(certificateExtractor, Mockito.never())
                .getDN(request);
    }

    @Test
    void authenticate_hasKeyOwnerHeaderButNoDn() {
        // The certificate is present in the request...
        // Note: The method exits early if keyOwner is blank, so we don't strictly need to mock getDN here,
        // but we'll simulate the environment having a DN but missing the specific header.
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(OWNER_META_KEY, OWNER);

        Mockito.when(certificateExtractor.getDN(Mockito.any()))
                .thenReturn(Optional.of(TEST_DN));

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);
        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_unknownDnAndKeyOwner() {
        // Given
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(OWNER_META_KEY, OWNER);

        Mockito.when(certificateExtractor.getDN(request))
                .thenReturn(Optional.of(TEST_DN));

        // Neither of these will match
        final CertificateIdentity otherIdentity1 = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OTHER_OWNER),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());
        final CertificateIdentity otherIdentity2 = new CertificateIdentity(
                OTHER_TEST_DN,
                Map.of(OWNER_META_KEY, OWNER),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        certificateIdentityService.addCertificateIdentity(otherIdentity1, Path.of("path1"));
        certificateIdentityService.addCertificateIdentity(otherIdentity2, Path.of("path2"));

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);
        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_knownDnAndKeyOwner() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(OWNER_META_KEY, OWNER);
        attributeMap.put("Foo", "Bar");

        final CertificateIdentity certificateIdentity = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER,
                        "Foo", "Bar"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());
        // Neither of these will match
        final CertificateIdentity otherIdentity1 = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OTHER_OWNER),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());
        final CertificateIdentity otherIdentity2 = new CertificateIdentity(
                OTHER_TEST_DN,
                Map.of(OWNER_META_KEY, OWNER),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());

        certificateIdentityService.addCertificateIdentity(certificateIdentity, Path.of("path1"));
        certificateIdentityService.addCertificateIdentity(otherIdentity1, Path.of("path2"));
        certificateIdentityService.addCertificateIdentity(otherIdentity2, Path.of("path3"));

        Mockito.when(certificateExtractor.getDN(request))
                .thenReturn(Optional.of(TEST_DN));

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);

        assertThat(result)
                .isPresent();
        assertThat(result.get())
                .isInstanceOf(DataFeedUserIdentity.class);
        assertThat(result.get().subjectId())
                .isEqualTo(DataFeedUserIdentity.SUBJECT_ID_PREFIX + OWNER);
        assertThat(attributeMap.get("Foo"))
                .isEqualTo("Bar");
    }

    @Test
    void authenticate_ignoreExpiredIdentities() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(OWNER_META_KEY, OWNER);
        attributeMap.put("Foo", "Bar");

        final CertificateIdentity certificateIdentity = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER,
                        "Foo", "Bar"),
                Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli());
        // Neither of these will match
        final CertificateIdentity otherIdentity1 = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER),
                Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
        final CertificateIdentity otherIdentity2 = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER),
                Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());

        certificateIdentityService.addCertificateIdentity(certificateIdentity, Path.of("path1"));
        certificateIdentityService.addCertificateIdentity(otherIdentity1, Path.of("path2"));
        certificateIdentityService.addCertificateIdentity(otherIdentity2, Path.of("path3"));

        Mockito.when(certificateExtractor.getDN(request))
                .thenReturn(Optional.of(TEST_DN));

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);

        assertThat(result)
                .isPresent();
        assertThat(result.get())
                .isInstanceOf(DataFeedUserIdentity.class);
        assertThat(result.get().subjectId())
                .isEqualTo(DataFeedUserIdentity.SUBJECT_ID_PREFIX + OWNER);
        assertThat(attributeMap.get("Foo"))
                .isEqualTo("Bar");
    }

    @Test
    void authenticate_allExpired() {
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(OWNER_META_KEY, OWNER);
        attributeMap.put("Foo", "Bar");

        final CertificateIdentity certificateIdentity = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER,
                        "Foo", "Bar"),
                Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
        // Neither of these will match
        final CertificateIdentity otherIdentity1 = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER),
                Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
        final CertificateIdentity otherIdentity2 = new CertificateIdentity(
                TEST_DN,
                Map.of(OWNER_META_KEY, OWNER),
                Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());

        certificateIdentityService.addCertificateIdentity(certificateIdentity, Path.of("path1"));
        certificateIdentityService.addCertificateIdentity(otherIdentity1, Path.of("path2"));
        certificateIdentityService.addCertificateIdentity(otherIdentity2, Path.of("path3"));

        Mockito.verify(certificateExtractor, Mockito.never())
                .getDN(request);

        final Optional<UserIdentity> result = certificateIdentityService.authenticate(request, attributeMap);

        assertThat(result)
                .isEmpty();
    }
}
