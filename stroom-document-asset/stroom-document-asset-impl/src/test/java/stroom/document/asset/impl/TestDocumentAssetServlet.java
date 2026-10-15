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

package stroom.document.asset.impl;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentAssetServlet {

    private static final Map<String, String> MIMETYPES = new DocumentAssetConfig().getMimetypes();
    private static final String DEFAULT_MIMETYPE = new DocumentAssetConfig().getDefaultMimetype();

    // --------------------------------------------------------------------------------
    // Mimetype resolution
    // --------------------------------------------------------------------------------

    @Test
    void mimetypeIsResolvedFromTheExtension() {
        assertThat(getMimetype("/index.html")).isEqualTo("text/html");
        assertThat(getMimetype("/img/logo.png")).isEqualTo("image/png");
        assertThat(getMimetype("/icons/desk.svg")).isEqualTo(DocumentAssetServlet.SVG_MIMETYPE);
    }

    @Test
    void anUpperCaseExtensionResolvesToTheSameMimetype() {
        // The map is keyed on lower case. Testing containsKey with a lower-cased extension and then
        // reading with the original case returned the default mimetype for 'LOGO.PNG', despite the
        // javadoc promising a match.
        assertThat(getMimetype("/img/LOGO.PNG")).isEqualTo("image/png");
        assertThat(getMimetype("/INDEX.HTML")).isEqualTo("text/html");
        assertThat(getMimetype("/icons/Desk.Svg")).isEqualTo(DocumentAssetServlet.SVG_MIMETYPE);
    }

    @Test
    void gifIsServedAsAGif() {
        // This mapped to image/jpeg.
        assertThat(getMimetype("/spinner.gif")).isEqualTo("image/gif");
    }

    @Test
    void anUnknownOrAbsentExtensionFallsBackToTheDefault() {
        assertThat(getMimetype("/data.parquet")).isEqualTo(DEFAULT_MIMETYPE);
        assertThat(getMimetype("/LICENCE")).isEqualTo(DEFAULT_MIMETYPE);
        assertThat(getMimetype("/trailing.")).isEqualTo(DEFAULT_MIMETYPE);
    }

    @Test
    void onlyTheExtensionAfterTheLastDotCounts() {
        assertThat(getMimetype("/v1.2/chart.min.js")).isEqualTo("text/javascript");
    }

    // --------------------------------------------------------------------------------
    // Security headers
    // --------------------------------------------------------------------------------

    @Test
    void anSvgIsServedAsADownloadAndCannotExecute() {
        final Map<String, String> headers = setSecurityHeaders(DocumentAssetServlet.SVG_MIMETYPE);

        // Navigating to an SVG asset makes it a document, where its own <script> elements run against
        // the Stroom origin with the viewing user's session. Downloading instead of rendering stops it
        // becoming a document; the inert policy stops execution if it somehow does.
        assertThat(headers).containsEntry("Content-Disposition", "attachment");
        assertThat(headers).containsEntry("Content-Security-Policy",
                DocumentAssetServlet.CONTENT_SECURITY_POLICY_INERT);
        assertThat(headers).containsEntry("X-Content-Type-Options", "nosniff");
    }

    @Test
    void theInertPolicyDeniesScriptingButAllowsInlineStyle() {
        final String policy = DocumentAssetServlet.CONTENT_SECURITY_POLICY_INERT;

        assertThat(policy).contains("default-src 'none'");
        assertThat(policy).contains("sandbox");
        // SVGs routinely carry a <style> block, so inline style has to stay allowed.
        assertThat(policy).contains("style-src 'unsafe-inline'");
    }

    @Test
    void nonSvgAssetKeepsRenderingButLosesCapabilitiesNoAssetNeeds() {
        final Map<String, String> headers = setSecurityHeaders("text/html");

        // Visualisations serve an executable index.html from this store, so an HTML asset must not be
        // turned into a download.
        assertThat(headers).doesNotContainKey("Content-Disposition");
        assertThat(headers).containsEntry("Content-Security-Policy",
                DocumentAssetServlet.CONTENT_SECURITY_POLICY_ASSET);
        assertThat(headers).containsEntry("X-Content-Type-Options", "nosniff");
    }

    @Test
    void theAssetPolicyKeepsScriptingAndDeniesTheRest() {
        final String policy = DocumentAssetServlet.CONTENT_SECURITY_POLICY_ASSET;

        // A visualisation's index.html implements the visualisationManager postMessage contract, so it
        // has to be able to run its own scripts.
        assertThat(policy).contains("script-src 'self' 'unsafe-eval' 'unsafe-inline'");
        assertThat(policy).contains("object-src 'none'");
        assertThat(policy).contains("form-action 'none'");
        // Without this an asset can re-point its own relative URLs at another origin.
        assertThat(policy).contains("base-uri 'none'");
    }

    @Test
    void everyMimetypeInTheDefaultMapGetsAPolicy() {
        // Nothing this servlet serves should come back without a content security policy, whatever
        // extension it was uploaded with.
        for (final String mimetype : MIMETYPES.values()) {
            assertThat(setSecurityHeaders(mimetype))
                    .as("policy for %s", mimetype)
                    .containsKey("Content-Security-Policy");
        }
        assertThat(setSecurityHeaders(DEFAULT_MIMETYPE)).containsKey("Content-Security-Policy");
    }

    // --------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------

    private String getMimetype(final String path) {
        return DocumentAssetServlet.getMimetype(path, MIMETYPES, DEFAULT_MIMETYPE);
    }

    /**
     * Calls the servlet's header logic against a response that records what it was given.
     *
     * @param mimetype The mimetype the asset is being served with.
     * @return The headers that were set, keyed on header name.
     */
    private Map<String, String> setSecurityHeaders(final String mimetype) {
        final Map<String, String> headers = new HashMap<>();
        final HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.doAnswer(invocation -> {
            headers.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(response).setHeader(Mockito.anyString(), Mockito.anyString());

        DocumentAssetServlet.setSecurityHeaders(response, mimetype);
        return headers;
    }
}
