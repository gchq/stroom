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

package stroom.gwt.workbench.framework.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomContent {

    private static final String BAR_META = "{\"type\": \"Visualisation\", \"uuid\": \"vis-1\", \"name\": \"Bar\", "
                                           + "\"functionName\": \"visualisations.Bar\"}";
    private static final String BAR_SETTINGS = "{\"tabs\": [],\n \"data\": {\"x\": \"<${x}>\"}}";

    @TempDir
    Path tempDir;

    private Path content;

    @BeforeEach
    void setUp() throws IOException {
        content = tempDir.resolve("stroom_content");
        final Path vis = Files.createDirectories(content.resolve("Visualisations/Version3"));
        final Path d3 = Files.createDirectories(vis.resolve("Dependencies/D3"));
        Files.writeString(vis.resolve("Bar.Visualisation.vis-1.meta"), BAR_META + "\n");
        Files.writeString(vis.resolve("Bar.Visualisation.vis-1.json"), BAR_SETTINGS);
        Files.writeString(vis.resolve("Bar.Visualisation.vis-1.node"), "name=Bar");
        Files.writeString(vis.resolve("Bar.Script.script-1.meta"), "{\"uuid\": \"script-1\"}");
        Files.writeString(vis.resolve("Bar.Script.script-1.js"), "// bar");
        // A name with a dot
        Files.writeString(d3.resolve("D3.v3.Script.script-d3.meta"), "{\"uuid\": \"script-d3\"}");
        Files.writeString(d3.resolve("D3.v3.Script.script-d3.js"), "// d3");
        // Not a document's file
        Files.writeString(vis.resolve("README.md"), "# readme");
    }

    @Test
    void testFindScript() {
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.findScript("script-1"))
                .contains(content.resolve("Visualisations/Version3/Bar.Script.script-1.js"));
        assertThat(stroomContent.findScript("script-d3"))
                .contains(content.resolve("Visualisations/Version3/Dependencies/D3/D3.v3.Script.script-d3.js"));
    }

    @Test
    void testFindScriptMissing() {
        final StroomContent stroomContent = new StroomContent(List.of(content));
        // A visualisation isn't a script, so its UUID finds nothing
        assertThat(stroomContent.findScript("vis-1")).isEmpty();
        assertThat(stroomContent.findScript("nope")).isEmpty();
        assertThat(stroomContent.findScript("")).isEmpty();
        assertThat(stroomContent.findScript(null)).isEmpty();
    }

    @Test
    void testFindScriptFirstRootWins() throws IOException {
        final Path other = Files.createDirectories(tempDir.resolve("other"));
        Files.writeString(other.resolve("Bar.Script.script-1.js"), "// other bar");
        final StroomContent stroomContent = new StroomContent(List.of(other, content));
        assertThat(stroomContent.findScript("script-1")).contains(other.resolve("Bar.Script.script-1.js"));
    }

    @Test
    void testMissingRootIsIgnored() {
        final StroomContent stroomContent = new StroomContent(List.of(tempDir.resolve("missing"), content));
        assertThat(stroomContent.findScript("script-1")).isPresent();
    }

    @Test
    void testVisualisationsJson() {
        final StroomContent stroomContent = new StroomContent(List.of(content));
        // The files' text is given as strings, escaped, and the scripts are in the order of their
        // paths
        assertThat(stroomContent.visualisationsJson()).isEqualTo(
                "{\"visualisations\":[{\"path\":\"Visualisations/Version3/Bar.Visualisation.vis-1.meta\","
                + "\"meta\":" + Json.quote(BAR_META + "\n") + ",\"settings\":"
                + "\"{\\\"tabs\\\": [],\\n \\\"data\\\": {\\\"x\\\": \\\"\\u003c${x}\\u003e\\\"}}\","
                + "\"indexAsset\":false}],"
                + "\"scripts\":["
                + "{\"path\":\"Visualisations/Version3/Bar.Script.script-1.meta\","
                + "\"meta\":\"{\\\"uuid\\\": \\\"script-1\\\"}\"},"
                + "{\"path\":\"Visualisations/Version3/Dependencies/D3/D3.v3.Script.script-d3.meta\","
                + "\"meta\":\"{\\\"uuid\\\": \\\"script-d3\\\"}\"}]}");
    }

    @Test
    void testVisualisationsJsonWithoutSettings() throws IOException {
        Files.delete(content.resolve("Visualisations/Version3/Bar.Visualisation.vis-1.json"));
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.visualisationsJson()).startsWith(
                "{\"visualisations\":[{\"path\":\"Visualisations/Version3/Bar.Visualisation.vis-1.meta\","
                + "\"meta\":" + Json.quote(BAR_META + "\n") + ",\"settings\":null,\"indexAsset\":false}]");
    }

    @Test
    void testVisualisationsJsonWithBrokenMeta() throws IOException {
        // A content pack can have an empty .meta, which is passed on for the reader to skip
        Files.writeString(content.resolve("Visualisations/Version3/Bar.Script.script-1.meta"), "");
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.visualisationsJson())
                .contains("{\"path\":\"Visualisations/Version3/Bar.Script.script-1.meta\",\"meta\":\"\"}");
    }

    @Test
    void testFindAsset() throws IOException {
        final Path assets = addAssets();
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.findAsset("vis-1", "index.html")).contains(assets.resolve("index.html"));
        assertThat(stroomContent.findAsset("vis-1", "lib/d3.v7.js")).contains(assets.resolve("lib/d3.v7.js"));
    }

    @Test
    void testFindAssetMissing() throws IOException {
        addAssets();
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.findAsset("vis-1", "nope.js")).isEmpty();
        // A directory isn't an asset
        assertThat(stroomContent.findAsset("vis-1", "lib")).isEmpty();
        // Another document has no assets
        assertThat(stroomContent.findAsset("script-1", "index.html")).isEmpty();
        assertThat(stroomContent.findAsset("", "index.html")).isEmpty();
        assertThat(stroomContent.findAsset(null, "index.html")).isEmpty();
        assertThat(stroomContent.findAsset("vis-1", "")).isEmpty();
        assertThat(stroomContent.findAsset("vis-1", null)).isEmpty();
    }

    @Test
    void testFindAssetCannotLeaveItsDirectory() throws IOException {
        addAssets();
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.findAsset("vis-1", "../Bar.Visualisation.vis-1.json")).isEmpty();
        assertThat(stroomContent.findAsset("vis-1", "lib/../../Bar.Script.script-1.js")).isEmpty();
        assertThat(stroomContent.findAsset("vis-1",
                content.resolve("Visualisations/Version3/Bar.Script.script-1.js").toString())).isEmpty();
    }

    @Test
    void testAssetsAreNotDocuments() throws IOException {
        final Path assets = addAssets();
        // Named as a document's file would be
        Files.writeString(assets.resolve("Other.Script.script-2.meta"), "{\"uuid\": \"script-2\"}");
        Files.writeString(assets.resolve("Other.Script.script-2.js"), "// other");
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.findScript("script-2")).isEmpty();
        assertThat(stroomContent.visualisationsJson()).doesNotContain("script-2");
    }

    @Test
    void testVisualisationsJsonSaysWhichAreAssetBased() throws IOException {
        addAssets();
        final StroomContent stroomContent = new StroomContent(List.of(content));
        assertThat(stroomContent.visualisationsJson()).contains(",\"indexAsset\":true}");
    }

    @Test
    void testVisualisationsJsonWithNoRoots() {
        assertThat(new StroomContent(List.of()).visualisationsJson())
                .isEqualTo("{\"visualisations\":[],\"scripts\":[]}");
    }

    @Test
    void testServedByWorkbenchServer() throws Exception {
        final WorkbenchServer server = new WorkbenchServer(0, List.of(tempDir), null,
                new StroomContent(List.of(content)));
        server.start();
        try (final HttpClient httpClient = HttpClient.newHttpClient()) {
            final String baseUrl = "http://localhost:" + server.getPort();
            // As VisFrame asks for it, relative to /ui/vis.html
            final HttpResponse<String> script = get(httpClient,
                    baseUrl + "/ui/script?uuid=script-1&name=Bar&ver=v1");
            assertThat(script.statusCode()).isEqualTo(200);
            assertThat(script.body()).isEqualTo("// bar");
            assertThat(script.headers().firstValue("Content-Type")).contains("text/javascript; charset=utf-8");

            assertThat(get(httpClient, baseUrl + "/ui/script?uuid=nope").statusCode()).isEqualTo(404);
            assertThat(get(httpClient, baseUrl + "/ui/script").statusCode()).isEqualTo(404);

            final HttpResponse<String> visualisations = get(httpClient,
                    baseUrl + StroomContent.VISUALISATIONS_PATH);
            assertThat(visualisations.statusCode()).isEqualTo(200);
            assertThat(visualisations.body()).startsWith("{\"visualisations\":[{\"path\":");
            assertThat(visualisations.headers().firstValue("Content-Type"))
                    .contains("application/json; charset=utf-8");
        } finally {
            server.stop();
        }
    }

    @Test
    void testAssetsServedByWorkbenchServer() throws Exception {
        addAssets();
        // The workbench's own assets, e.g. its test visualisations'
        Files.createDirectories(tempDir.resolve("assets/vis-test"));
        Files.writeString(tempDir.resolve("assets/vis-test/index.html"), "<p>test</p>");
        final WorkbenchServer server = new WorkbenchServer(0, List.of(tempDir), null,
                new StroomContent(List.of(content)));
        server.start();
        try (final HttpClient httpClient = HttpClient.newHttpClient()) {
            final String baseUrl = "http://localhost:" + server.getPort();
            // As VisController loads an asset-based visualisation's frame
            final HttpResponse<String> index = get(httpClient, baseUrl + "/assets/vis-1/index.html");
            assertThat(index.statusCode()).isEqualTo(200);
            assertThat(index.body()).isEqualTo("<html></html>");
            assertThat(index.headers().firstValue("Content-Type")).contains("text/html; charset=utf-8");
            assertThat(get(httpClient, baseUrl + "/assets/vis-1/lib/d3.v7.js").body()).isEqualTo("// d3 7");

            assertThat(get(httpClient, baseUrl + "/assets/vis-test/index.html").body()).isEqualTo("<p>test</p>");
            assertThat(get(httpClient, baseUrl + "/assets/vis-1/nope.js").statusCode()).isEqualTo(404);
        } finally {
            server.stop();
        }
    }

    @Test
    void testQueryParam() {
        assertThat(WorkbenchServer.queryParam("uuid=a&name=b", "uuid")).isEqualTo("a");
        assertThat(WorkbenchServer.queryParam("uuid=a&name=b", "name")).isEqualTo("b");
        assertThat(WorkbenchServer.queryParam("name=My%20Script&uuid=a%2Db", "name")).isEqualTo("My Script");
        assertThat(WorkbenchServer.queryParam("name=My%20Script&uuid=a%2Db", "uuid")).isEqualTo("a-b");
        assertThat(WorkbenchServer.queryParam("flag&uuid=a", "flag")).isEmpty();
        assertThat(WorkbenchServer.queryParam("uuid=a", "name")).isNull();
        assertThat(WorkbenchServer.queryParam(null, "uuid")).isNull();
    }

    // Gives the visualisation Bar path assets, as the visualisations' build does, and returns
    // their directory
    private Path addAssets() throws IOException {
        final Path assets = Files.createDirectories(
                        content.resolve("Visualisations/Version3/Bar.Visualisation.vis-1-path-assets/lib"))
                .getParent();
        Files.writeString(assets.resolve("index.html"), "<html></html>");
        Files.writeString(assets.resolve("lib/d3.v7.js"), "// d3 7");
        return assets;
    }

    private static HttpResponse<String> get(final HttpClient httpClient, final String url) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
