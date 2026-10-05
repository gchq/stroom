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

import org.junit.jupiter.api.AfterEach;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkbenchApi {

    @TempDir
    Path tempDir;

    private Path sourceRoot;
    private Path storiesRoot;
    private WorkbenchServer server;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        sourceRoot = tempDir.resolve("src");
        storiesRoot = tempDir.resolve("stories");
        write(sourceRoot, "a/client/Button.java", "public class Button {}");
        write(storiesRoot, "app/AllStories.java", "package app;\n\nclass AllStories {\n    return registry;\n}\n");
        write(storiesRoot, "app/widgets/ButtonStories.java", """
                import a.client.Button;

                public final class ButtonStories {
                    static void addTo(StoryRegistry registry) {
                        registry.component("Widgets/Button", ButtonStories.class)
                                .story("Primary", context -> new Button());
                    }
                }
                """);

        // 'true' is an editor that does nothing
        startServer("true");
        httpClient = HttpClient.newHttpClient();
    }

    private void startServer(final String editor) throws IOException {
        if (server != null) {
            server.stop();
        }
        final WorkbenchApi api = new WorkbenchApi(
                new SourceFinder(List.of(sourceRoot, storiesRoot)),
                new EditorLauncher(Map.of(EditorLauncher.EDITOR_ENV_VAR, editor)),
                new StoryFileCreator(storiesRoot, "app.widgets", storiesRoot.resolve("app/AllStories.java")),
                new GitChanges(tempDir));
        server = new WorkbenchServer(0, List.of(tempDir), api);
        server.start();
        baseUrl = "http://localhost:" + server.getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        httpClient.close();
    }

    @Test
    void testOpenInEditor() throws Exception {
        final HttpResponse<String> response = post(
                "open-in-editor?class=app.widgets.ButtonStories&id=widgets-button--primary", baseUrl);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ButtonStories.java").contains("\"line\":6");
    }

    @Test
    void testOpenInEditorUnknownClass() throws Exception {
        final HttpResponse<String> response = post("open-in-editor?class=a.Missing", baseUrl);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"error\":\"Can't find the source of a.Missing\"");
    }

    @Test
    void testOpenInEditorRequiresPost() throws Exception {
        assertThat(get("open-in-editor?class=a.client.Button", baseUrl).statusCode()).isEqualTo(405);
    }

    @Test
    void testRejectsOtherOrigins() throws Exception {
        assertThat(post("open-in-editor?class=a.client.Button", "http://evil.example.com").statusCode())
                .isEqualTo(403);
        assertThat(get("components?q=", "http://localhost:1").statusCode()).isEqualTo(403);
    }

    @Test
    void testRejectsCrossSiteRequestsWithoutOrigin() throws Exception {
        final HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + WorkbenchApi.PATH + "components?q="))
                        .header("Sec-Fetch-Site", "cross-site")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void testAllowsOtherLoopbackOrigins() throws Exception {
        final int port = server.getPort();
        assertThat(post("open-in-editor?class=a.client.Button", "http://127.0.0.1:" + port).statusCode())
                .isEqualTo(200);
        assertThat(post("open-in-editor?class=a.client.Button", "http://[::1]:" + port).statusCode())
                .isEqualTo(200);
    }

    @Test
    void testRejectsPostWithoutOrigin() throws Exception {
        assertThat(post("open-in-editor?class=a.client.Button", null).statusCode()).isEqualTo(403);
        write(sourceRoot, "a/client/Menu.java", "public class Menu {}");
        assertThat(post("create-story?class=a.client.Menu", null).statusCode()).isEqualTo(403);
        assertThat(storiesRoot.resolve("app/widgets/MenuStories.java")).doesNotExist();
    }

    @Test
    void testRejectsOtherHosts() throws Exception {
        // E.g. DNS rebinding, where another site's name points at the loopback address
        final int port = server.getPort();
        assertThat(RawHttp.send(port, "GET", WorkbenchApi.PATH + "components?q=",
                "Host: evil.example.com:" + port)).isEqualTo(403);
        assertThat(RawHttp.send(port, "POST", WorkbenchApi.PATH + "open-in-editor?class=a.client.Button",
                "Host: evil.example.com:" + port, "Origin: http://evil.example.com:" + port)).isEqualTo(403);
        assertThat(RawHttp.send(port, "GET", WorkbenchApi.PATH + "components?q=",
                "Host: localhost:" + port)).isEqualTo(200);
    }

    @Test
    void testEditorThatCantStartGivesJsonError() throws Exception {
        startServer(tempDir.resolve("missing-editor").toString());
        final HttpResponse<String> response = post("open-in-editor?class=a.client.Button", baseUrl);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json; charset=utf-8");
        assertThat(response.body()).startsWith("{\"error\":").contains("missing-editor");
    }

    @Test
    void testComponents() throws Exception {
        final HttpResponse<String> response = get("components?q=but", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[{\"className\":\"a.client.Button\",\"name\":\"Button\","
                                              + "\"path\":\"a/client/Button.java\"}]");
    }

    @Test
    void testCreateStory() throws Exception {
        write(sourceRoot, "a/client/Menu.java", "public class Menu {}");
        final HttpResponse<String> response = post("create-story?class=a.client.Menu", baseUrl);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"storyId\":\"widgets-menu--default\"");
        assertThat(storiesRoot.resolve("app/widgets/MenuStories.java")).exists();

        // A second time fails as the stories exist
        assertThat(post("create-story?class=a.client.Menu", baseUrl).statusCode()).isEqualTo(409);
    }

    @Test
    void testCreateStoryWithoutNoArgConstructor() throws Exception {
        write(sourceRoot, "a/client/Dialog.java", """
                package a.client;

                public class Dialog {
                    public Dialog(final String title) {
                    }
                }
                """);
        assertThat(post("create-story?class=a.client.Dialog", baseUrl).statusCode()).isEqualTo(200);
        assertThat(storiesRoot.resolve("app/widgets/DialogStories.java")).content()
                .contains("new Label(\"Construct a Dialog here.\")")
                .doesNotContain("new Dialog(");
    }

    @Test
    void testCreateStoryUnknownClass() throws Exception {
        assertThat(post("create-story?class=a.client.Missing", baseUrl).statusCode()).isEqualTo(404);
    }

    @Test
    void testChanges() throws Exception {
        // Not a git repository so nothing has changed
        final HttpResponse<String> response = get("changes?classes=a.client.Button,a.Missing", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"new\":[],\"modified\":[],\"related\":[]}");
    }

    @Test
    void testChangesInGit() throws Exception {
        GitTestUtil.init(tempDir);
        GitTestUtil.commitAll(tempDir);
        write(sourceRoot, "a/client/Button.java", "public class Button {\n}");
        write(storiesRoot, "app/widgets/MenuStories.java", "public final class MenuStories {}");

        final HttpResponse<String> response = get(
                "changes?classes=app.widgets.ButtonStories,a.client.Button,app.widgets.MenuStories", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"new\":[\"app.widgets.MenuStories\"],"
                                              + "\"modified\":[\"a.client.Button\"],"
                                              + "\"related\":[\"app.widgets.ButtonStories\"]}");
    }

    @Test
    void testUnknownApi() throws Exception {
        assertThat(get("missing", null).statusCode()).isEqualTo(404);
    }

    @Test
    void testParseQuery() {
        assertThat(WorkbenchApi.parseQuery(null)).isEmpty();
        assertThat(WorkbenchApi.parseQuery("")).isEmpty();
        assertThat(WorkbenchApi.parseQuery("a=1&b=x%20y&c=&=d&e"))
                .containsExactlyInAnyOrderEntriesOf(Map.of("a", "1", "b", "x y", "c", ""));
    }

    private HttpResponse<String> get(final String path, final String origin) throws Exception {
        return send(path, "GET", origin);
    }

    private HttpResponse<String> post(final String path, final String origin) throws Exception {
        return send(path, "POST", origin);
    }

    private HttpResponse<String> send(final String path, final String method, final String origin)
            throws Exception {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + WorkbenchApi.PATH + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (origin != null) {
            builder.header("Origin", origin);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void write(final Path root, final String path, final String content) throws IOException {
        final Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
