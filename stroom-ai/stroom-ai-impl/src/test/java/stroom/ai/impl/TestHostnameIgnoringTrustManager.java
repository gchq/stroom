/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHostnameIgnoringTrustManager {

    private static final X509Certificate[] CHAIN = new X509Certificate[0];

    private final X509ExtendedTrustManager delegate = Mockito.mock(X509ExtendedTrustManager.class);
    private final X509ExtendedTrustManager trustManager = (X509ExtendedTrustManager) HostnameIgnoringTrustManager
            .wrap(new TrustManager[]{delegate})[0];

    @Test
    void serversAreCheckedWithoutTheirHostName() throws CertificateException {
        // The engine and socket are how a trust manager finds the host name, so the delegate isn't given them.
        trustManager.checkServerTrusted(CHAIN, "RSA", Mockito.mock(SSLEngine.class));
        trustManager.checkServerTrusted(CHAIN, "RSA", Mockito.mock(Socket.class));
        trustManager.checkServerTrusted(CHAIN, "RSA");

        Mockito.verify(delegate, Mockito.times(3)).checkServerTrusted(CHAIN, "RSA");
        Mockito.verify(delegate, Mockito.never())
                .checkServerTrusted(Mockito.any(), Mockito.anyString(), Mockito.any(SSLEngine.class));
        Mockito.verify(delegate, Mockito.never())
                .checkServerTrusted(Mockito.any(), Mockito.anyString(), Mockito.any(Socket.class));
    }

    @Test
    void certificatesTheDelegateDoesNotTrustAreStillRefused() throws CertificateException {
        Mockito.doThrow(new CertificateException("untrusted")).when(delegate).checkServerTrusted(CHAIN, "RSA");

        assertThatThrownBy(() -> trustManager.checkServerTrusted(CHAIN, "RSA", Mockito.mock(SSLEngine.class)))
                .isInstanceOf(CertificateException.class)
                .hasMessage("untrusted");
    }

    @Test
    void clientsAreCheckedByTheDelegate() throws CertificateException {
        trustManager.checkClientTrusted(CHAIN, "RSA", Mockito.mock(SSLEngine.class));

        Mockito.verify(delegate).checkClientTrusted(CHAIN, "RSA");
        assertThat(trustManager.getAcceptedIssuers()).isNull();
    }

    @Test
    void trustManagersThatAreNotForCertificatesAreLeftAsTheyAre() {
        final TrustManager other = Mockito.mock(TrustManager.class);

        assertThat(HostnameIgnoringTrustManager.wrap(new TrustManager[]{other})).containsExactly(other);
    }
}
