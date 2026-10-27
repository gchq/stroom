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

package stroom.http.client.component;

import stroom.credentials.client.presenter.CredentialClient;
import stroom.util.shared.http.HttpTlsConfig;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestHttpTlsConfigEditorController {

    private HttpTlsConfigEditor view;
    private HttpTlsConfigEditorController controller;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        // The form is a widget, which can only be made in a browser
        view = Mockito.mock(HttpTlsConfigEditor.class);
        controller = new HttpTlsConfigEditorController(
                new SimpleEventBus(), view, Mockito.mock(CredentialClient.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void read_noConfigShowsTheDefaults() {
        // Regression test: with no TLS config yet the view was left as it started, so Verify Host
        // Name showed unticked and OK saved it as false, though it defaults to true
        controller.read(null);

        Mockito.verify(view).setVerifyHostname(true);
        Mockito.verify(view).setTrustSelfSignedCertificates(false);
        Mockito.verify(view).setProtocol("TLSv1.3");
    }

    @Test
    void read_config() {
        controller.read(HttpTlsConfig.builder()
                .protocol("TLSv1.2")
                .verifyHostname(false)
                .trustSelfSignedCertificates(true)
                .build());

        Mockito.verify(view).setVerifyHostname(false);
        Mockito.verify(view).setTrustSelfSignedCertificates(true);
        Mockito.verify(view).setProtocol("TLSv1.2");
    }
}
