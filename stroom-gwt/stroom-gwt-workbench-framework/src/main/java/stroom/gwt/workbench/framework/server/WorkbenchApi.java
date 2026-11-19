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

import stroom.gwt.workbench.framework.server.GitChanges.Changes;
import stroom.gwt.workbench.framework.server.SourceFinder.Component;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The workbench server's API, used by the manager for the features React Storybook gets from
/// its dev server:
///
/// * `POST open-in-editor?class=<class>&id=<story id>` - opens a story's source in an editor.
/// * `GET components?q=<text>` - lists components that could have stories.
/// * `POST create-story?class=<class>` - creates stories for a component.
/// * `GET changes?classes=<class>,...` - which of the classes are new, modified or related to a
///   change in git.
///
/// As the API can start programs and write files, requests from other sites, or made using a host
/// name other than the loopback interface's, are rejected.
public class WorkbenchApi implements HttpHandler {

    /// The path the API is served under.
    public static final String PATH = "/__workbench/api/";

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkbenchApi.class);
    private static final int MAX_COMPONENTS = 50;

    private final SourceFinder sourceFinder;
    private final EditorLauncher editorLauncher;
    private final StoryFileCreator storyFileCreator;
    private final GitChanges gitChanges;

    /// Requests are only allowed from the port the request was received on, so the server can
    /// listen on any port.
    ///
    /// @param sourceFinder     Finds source files.
    /// @param editorLauncher   Opens files in an editor.
    /// @param storyFileCreator Creates stories, or null if not configured.
    /// @param gitChanges       Finds changed files.
    public WorkbenchApi(final SourceFinder sourceFinder,
                        final EditorLauncher editorLauncher,
                        final StoryFileCreator storyFileCreator,
                        final GitChanges gitChanges) {
        this.sourceFinder = sourceFinder;
        this.editorLauncher = editorLauncher;
        this.storyFileCreator = storyFileCreator;
        this.gitChanges = gitChanges;
    }

    /// Handles an API request, sending a JSON error with status 500 if it fails.
    ///
    /// @param exchange The request and response.
    @Override
    public void handle(final HttpExchange exchange) {
        try {
            handleRequest(exchange);
        } catch (final IOException | RuntimeException e) {
            LOGGER.error("Error handling {}", exchange.getRequestURI(), e);
            sendErrorIfPossible(exchange, e);
        } finally {
            exchange.close();
        }
    }

    private void handleRequest(final HttpExchange exchange) throws IOException {
        if (!LocalRequests.isAllowedHost(exchange)) {
            send(exchange, 403, error("Requests must be made to localhost"));
            return;
        }
        final String method = exchange.getRequestMethod();
        if (!isSameOrigin(exchange, method)) {
            send(exchange, 403, error("Requests from other sites aren't allowed"));
            return;
        }
        final String endpoint = exchange.getRequestURI().getPath().substring(PATH.length());
        final Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        switch (endpoint) {
            case "open-in-editor":
                requirePost(exchange, method, () -> openInEditor(exchange, params));
                break;
            case "components":
                send(exchange, 200, components(params.get("q")));
                break;
            case "create-story":
                requirePost(exchange, method, () -> createStory(exchange, params));
                break;
            case "changes":
                send(exchange, 200, changes(params.get("classes")));
                break;
            default:
                send(exchange, 404, error("Unknown API " + endpoint));
                break;
        }
    }

    private static void sendErrorIfPossible(final HttpExchange exchange, final Exception e) {
        // Once the headers have gone the status can't be changed
        if (exchange.getResponseCode() == -1) {
            try {
                send(exchange, 500, error(String.valueOf(e.getMessage())));
            } catch (final IOException | RuntimeException sendError) {
                LOGGER.debug("Unable to send the error for {}", exchange.getRequestURI(), sendError);
            }
        }
    }

    /// Only allows requests from the workbench's own pages, so other web sites can't start
    /// programs or write files.
    ///
    /// Browsers always send `Origin` with a `POST`, so it is required for them. A same-origin
    /// `GET` may have no `Origin`, so then `Sec-Fetch-Site` is checked if it is present.
    private boolean isSameOrigin(final HttpExchange exchange, final String method) {
        final int port = exchange.getLocalAddress().getPort();
        final String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null && !"POST".equals(method)) {
            final String site = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
            return site == null || "same-origin".equals(site) || "none".equals(site);
        }
        return LocalRequests.isAllowedOrigin(origin, port);
    }

    private void requirePost(final HttpExchange exchange, final String method, final IoAction action)
            throws IOException {
        if (!"POST".equals(method)) {
            send(exchange, 405, error("Use POST"));
        } else {
            action.run();
        }
    }

    private void openInEditor(final HttpExchange exchange, final Map<String, String> params) throws IOException {
        final Optional<Path> file = sourceFinder.findFile(params.get("class"));
        if (file.isEmpty()) {
            send(exchange, 404, error("Can't find the source of " + params.get("class")));
            return;
        }
        final int line = sourceFinder.findStoryLine(file.get(), params.get("id"));
        if (editorLauncher.open(file.get(), line)) {
            LOGGER.info("Opened {}:{} in an editor", file.get(), line);
            send(exchange, 200, "{\"file\":" + Json.quote(file.get().toString()) + ",\"line\":" + line + "}");
        } else {
            send(exchange, 501, error("No editor found. Set the " + EditorLauncher.EDITOR_ENV_VAR
                                      + " environment variable, e.g. to 'idea' or 'code', and restart "
                                      + "the workbench server."));
        }
    }

    private String components(final String query) {
        final List<String> items = new ArrayList<>();
        for (final Component component : sourceFinder.findComponents(query, MAX_COMPONENTS)) {
            items.add("{\"className\":" + Json.quote(component.getClassName())
                      + ",\"name\":" + Json.quote(component.getSimpleName())
                      + ",\"path\":" + Json.quote(component.getPath()) + "}");
        }
        return "[" + String.join(",", items) + "]";
    }

    private void createStory(final HttpExchange exchange, final Map<String, String> params) throws IOException {
        if (storyFileCreator == null) {
            send(exchange, 501, error("Creating stories isn't configured for this workbench"));
            return;
        }
        final String className = params.get("class");
        final Optional<Path> componentFile = sourceFinder.findFile(className);
        if (componentFile.isEmpty()) {
            send(exchange, 404, error("Can't find the source of " + className));
            return;
        }
        try {
            final StoryFileCreator.Result result = storyFileCreator.create(className, componentFile.get());
            LOGGER.info("Created {}", result.getFile());
            send(exchange, 200, "{\"file\":" + Json.quote(result.getFile().toString())
                                + ",\"storyId\":" + Json.quote(result.getStoryId()) + "}");
        } catch (final IllegalStateException | IllegalArgumentException e) {
            send(exchange, 409, error(e.getMessage()));
        }
    }

    private String changes(final String classes) {
        final Changes changes = gitChanges.find();
        // Git gives real paths, so compare real paths in case a source root is a symbolic link
        final Set<Path> added = toRealPaths(changes.getAdded());
        final Set<Path> modified = toRealPaths(changes.getModified());
        final List<String> newClasses = new ArrayList<>();
        final List<String> modifiedClasses = new ArrayList<>();
        final List<String> relatedClasses = new ArrayList<>();

        if (classes != null && !classes.isEmpty()) {
            for (final String className : classes.split(",")) {
                final Optional<Path> file = sourceFinder.findFile(className);
                if (file.isEmpty()) {
                    continue;
                }
                final Path realFile = FilePaths.realPath(file.get());
                if (added.contains(realFile)) {
                    newClasses.add(className);
                } else if (modified.contains(realFile)) {
                    modifiedClasses.add(className);
                } else if (importsChangedClass(file.get(), added, modified)) {
                    // The stories haven't changed but the component they show has
                    relatedClasses.add(className);
                }
            }
        }
        return "{\"new\":" + Json.array(newClasses)
               + ",\"modified\":" + Json.array(modifiedClasses)
               + ",\"related\":" + Json.array(relatedClasses) + "}";
    }

    private boolean importsChangedClass(final Path file, final Set<Path> added, final Set<Path> modified) {
        for (final String imported : sourceFinder.findImports(file)) {
            final Optional<Path> importedFile = sourceFinder.findFile(imported).map(FilePaths::realPath);
            if (importedFile.isPresent()
                && (added.contains(importedFile.get()) || modified.contains(importedFile.get()))) {
                return true;
            }
        }
        return false;
    }

    private static Set<Path> toRealPaths(final List<Path> paths) {
        final Set<Path> realPaths = new HashSet<>();
        for (final Path path : paths) {
            realPaths.add(FilePaths.realPath(path));
        }
        return realPaths;
    }

    /// Parses a URL query string. Parameters without a name or `=` are ignored.
    ///
    /// @param rawQuery The raw (still encoded) query, may be null.
    /// @return The decoded parameter values by name. If a name repeats, the last value wins.
    static Map<String, String> parseQuery(final String rawQuery) {
        final Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (final String pair : rawQuery.split("&")) {
            final int index = pair.indexOf('=');
            if (index > 0) {
                params.put(URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static String error(final String message) {
        return "{\"error\":" + Json.quote(message) + "}";
    }

    private static void send(final HttpExchange exchange, final int status, final String json) throws IOException {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (final OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    private interface IoAction {

        void run() throws IOException;
    }
}
