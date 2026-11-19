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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// A minimal static file server for the workbench.
///
/// GWT no longer bundles a servlet container, and the workbench only needs static files, so
/// this serves them using the JDK's built-in HTTP server. Files are looked up in an ordered
/// list of root directories, the first root containing the requested path wins.
/// Only listens on the loopback interface as it is a development tool, and only answers
/// requests made to a loopback host name.
///
/// It can also serve the visualisations and scripts of directories of Stroom content (see
/// [StroomContent]), as Stroom does, so that the workbench can show real visualisations.
public class WorkbenchServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkbenchServer.class);

    private static final String INDEX_FILE = "index.html";
    // Where the workbench's webapp files that stand for Stroom's REST API are (e.g. a zone dictionary
    // that FloorMap reads), and the header Stroom requires of a change made from a browser
    private static final String API_PATH = "/api/";
    private static final String CSRF_HEADER = "X-CSRF";

    private static final String SOURCE_ROOT_OPTION = "--source-root";
    private static final String CONTENT_ROOT_OPTION = "--content-root";
    private static final String STORIES_ROOT_OPTION = "--stories-root";
    private static final String STORIES_PACKAGE_OPTION = "--stories-package";
    private static final String ALL_STORIES_OPTION = "--all-stories";
    private static final List<String> STORIES_OPTIONS = List.of(
            STORIES_ROOT_OPTION, STORIES_PACKAGE_OPTION, ALL_STORIES_OPTION);

    private static final String USAGE = "Usage: WorkbenchServer <port> [--source-root=<dir>]... "
                                        + "[--content-root=<dir>]... [--stories-root=<dir> --stories-package=<package> "
                                        + "--all-stories=<file>] <root dir>...";

    private final StaticFileResolver resolver;
    private final int port;
    private final WorkbenchApi api;
    private final StroomContent content;
    private HttpServer server;
    private ExecutorService executor;

    /// Creates a server that will serve files from the supplied roots, without an API.
    ///
    /// @param port  The port to listen on, or 0 for any free port.
    /// @param roots The root directories to serve files from, in order of precedence.
    public WorkbenchServer(final int port, final List<Path> roots) {
        this(port, roots, null);
    }

    /// Creates a server that will serve files from the supplied roots and an API.
    ///
    /// @param port  The port to listen on, or 0 for any free port.
    /// @param roots The root directories to serve files from, in order of precedence.
    /// @param api   The API, served under [WorkbenchApi#PATH], may be null.
    public WorkbenchServer(final int port, final List<Path> roots, final WorkbenchApi api) {
        this(port, roots, api, new StroomContent(List.of()));
    }

    /// Creates a server that will serve files from the supplied roots, an API and Stroom content.
    ///
    /// @param port    The port to listen on, or 0 for any free port.
    /// @param roots   The root directories to serve files from, in order of precedence.
    /// @param api     The API, served under [WorkbenchApi#PATH], may be null.
    /// @param content The visualisations and scripts, served under [StroomContent#SCRIPT_PATH] and
    ///                [StroomContent#VISUALISATIONS_PATH].
    public WorkbenchServer(final int port,
                           final List<Path> roots,
                           final WorkbenchApi api,
                           final StroomContent content) {
        this.port = port;
        this.resolver = new StaticFileResolver(roots);
        this.api = api;
        this.content = Objects.requireNonNull(content);
    }

    /// Starts the server with the arguments
    /// `<port> [--source-root=<dir>]... [--content-root=<dir>]... [--stories-root=<dir>
    /// --stories-package=<package> --all-stories=<file>] <root dir>...`.
    ///
    /// The source roots let the API open stories in an editor and list components. The content
    /// roots are directories of Stroom content whose visualisations to serve (see
    /// [StroomContent]). The stories options let it create new stories.
    ///
    /// @param args The port, options, then one or more root directories to serve.
    /// @throws IOException If the server can't be started.
    public static void main(final String[] args) throws IOException {
        create(args).start();
    }

    /// Creates, but doesn't start, a server from the arguments that [#main(String[])] takes.
    ///
    /// @param args The port, options, then one or more root directories to serve.
    /// @return The server.
    /// @throws IllegalArgumentException If the arguments are invalid, e.g. only some of the
    ///                                  stories options are given.
    static WorkbenchServer create(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException(USAGE);
        }
        final int port = Integer.parseInt(args[0]);
        final List<Path> roots = new ArrayList<>();
        final List<Path> sourceRoots = new ArrayList<>();
        final List<Path> contentRoots = new ArrayList<>();
        final Map<String, String> storiesOptions = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            final String arg = args[i];
            if (arg.startsWith("--") && !arg.contains("=")) {
                throw new IllegalArgumentException("Option " + arg + " needs a value\n" + USAGE);
            }
            final String optionName = arg.contains("=")
                    ? arg.substring(0, arg.indexOf('='))
                    : arg;
            if (SOURCE_ROOT_OPTION.equals(optionName)) {
                sourceRoots.add(Path.of(optionValue(arg)));
            } else if (CONTENT_ROOT_OPTION.equals(optionName)) {
                contentRoots.add(Path.of(optionValue(arg)));
            } else if (STORIES_OPTIONS.contains(optionName)) {
                storiesOptions.put(optionName, optionValue(arg));
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option " + arg + "\n" + USAGE);
            } else {
                roots.add(Path.of(arg).toAbsolutePath().normalize());
            }
        }

        if (!storiesOptions.isEmpty() && storiesOptions.size() != STORIES_OPTIONS.size()) {
            throw new IllegalArgumentException("To create stories all of " + String.join(", ", STORIES_OPTIONS)
                                               + " must be given\n" + USAGE);
        }
        if (roots.isEmpty()) {
            LOGGER.warn("No root directories to serve files from were given, so only the API will work");
        }

        final WorkbenchApi api = sourceRoots.isEmpty()
                ? null
                : createApi(sourceRoots, storiesOptions);
        return new WorkbenchServer(port, roots, api, new StroomContent(contentRoots));
    }

    private static WorkbenchApi createApi(final List<Path> sourceRoots, final Map<String, String> storiesOptions) {
        final StoryFileCreator creator = storiesOptions.isEmpty()
                ? null
                : new StoryFileCreator(Path.of(storiesOptions.get(STORIES_ROOT_OPTION)),
                        storiesOptions.get(STORIES_PACKAGE_OPTION),
                        Path.of(storiesOptions.get(ALL_STORIES_OPTION)));
        return new WorkbenchApi(new SourceFinder(sourceRoots), new EditorLauncher(System.getenv()),
                creator, new GitChanges(sourceRoots.getFirst()));
    }

    private static String optionValue(final String arg) {
        return arg.substring(arg.indexOf('=') + 1);
    }

    /// Starts listening for requests. Returns once the server has started.
    ///
    /// @throws IOException If the server can't listen on the port.
    public synchronized void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("Already started");
        }
        final HttpServer newServer = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        newServer.createContext("/", this::handle);
        if (api != null) {
            newServer.createContext(WorkbenchApi.PATH, api);
        }
        // Requests can be slow, e.g. finding git changes, so don't make others wait for them
        executor = Executors.newVirtualThreadPerTaskExecutor();
        newServer.setExecutor(executor);
        newServer.start();
        server = newServer;
        LOGGER.info("Serving workbench on http://localhost:{}/ from {}", getPort(), resolver.getRoots());
        if (!content.getRoots().isEmpty()) {
            LOGGER.info("Serving the visualisations in {}", content.getRoots());
        }
    }

    /// Stops the server if it is running.
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    /// @return The port the server listens on. Once started this is the actual port, which is
    /// useful when the server was created with port 0.
    public synchronized int getPort() {
        return server != null
                ? server.getAddress().getPort()
                : port;
    }

    private void handle(final HttpExchange exchange) {
        try {
            serveFile(exchange);
        } catch (final IOException | RuntimeException e) {
            LOGGER.error("Error serving {}", exchange.getRequestURI(), e);
            sendErrorIfPossible(exchange);
        } finally {
            exchange.close();
        }
    }

    private void serveFile(final HttpExchange exchange) throws IOException {
        if (!LocalRequests.isAllowedHost(exchange)) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        final String method = exchange.getRequestMethod();
        if ("PUT".equals(method) && exchange.getRequestURI().getPath().startsWith(API_PATH)) {
            acceptDocumentSave(exchange);
            return;
        }
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        final String path = exchange.getRequestURI().getPath();
        if (StroomContent.SCRIPT_PATH.equals(path)) {
            serveScript(exchange);
            return;
        }
        if (StroomContent.VISUALISATIONS_PATH.equals(path)) {
            send(exchange, content.visualisationsJson().getBytes(StandardCharsets.UTF_8),
                    ContentTypes.forFileName(Path.of("visualisations.json")));
            return;
        }

        final String requestPath = path.endsWith("/")
                ? path + INDEX_FILE
                : path;

        // A document's path asset from the Stroom content, if it has one; otherwise the
        // workbench's own (e.g. its test visualisations' under webapp/assets/)
        if (requestPath.startsWith(StroomContent.ASSETS_PATH)) {
            final Optional<Path> asset = findContentAsset(requestPath);
            if (asset.isPresent()) {
                send(exchange, Files.readAllBytes(asset.get()), ContentTypes.forFileName(asset.get()));
                return;
            }
        }

        final Optional<Path> file = resolver.resolve(requestPath);
        if (file.isEmpty()) {
            LOGGER.debug("Not found: {}", requestPath);
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        send(exchange, Files.readAllBytes(file.get()), ContentTypes.forFileName(file.get()));
    }

    // A visualisation saving a document it read from the workbench's webapp files under /api/ (e.g.
    // FloorMap saving a zone dictionary), answered as Stroom answers it, without storing it: refused
    // (403) without the X-CSRF header Stroom requires of a change made from a browser, not found
    // (404) if there is no such document, and otherwise the document as sent
    private void acceptDocumentSave(final HttpExchange exchange) throws IOException {
        final byte[] body;
        try (final InputStream inputStream = exchange.getRequestBody()) {
            body = inputStream.readAllBytes();
        }
        if (!"1".equals(exchange.getRequestHeaders().getFirst(CSRF_HEADER))) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        if (resolver.resolve(exchange.getRequestURI().getPath()).isEmpty()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        send(exchange, body, ContentTypes.forFileName(Path.of("document.json")));
    }

    // A path asset of the Stroom content, by a path of /assets/<uuid>/<asset's path>
    private Optional<Path> findContentAsset(final String requestPath) {
        final String rest = requestPath.substring(StroomContent.ASSETS_PATH.length());
        final int slash = rest.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        return content.findAsset(rest.substring(0, slash), rest.substring(slash + 1));
    }

    // A script of the Stroom content, by the UUID in the query, as Stroom's ScriptServlet serves it
    private void serveScript(final HttpExchange exchange) throws IOException {
        final String uuid = queryParam(exchange.getRequestURI().getRawQuery(), "uuid");
        final Optional<Path> file = content.findScript(uuid);
        if (file.isEmpty()) {
            LOGGER.debug("No script: {}", uuid);
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        send(exchange, Files.readAllBytes(file.get()), ContentTypes.forFileName(file.get()));
    }

    private static void send(final HttpExchange exchange,
                             final byte[] bytes,
                             final String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // Never cache so that recompiles are always picked up
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, bytes.length);
            try (final OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    /// @param rawQuery A request's query, still encoded, may be null.
    /// @param name     A parameter's name.
    /// @return The decoded value of the parameter, empty if it has no value, or null if the query
    /// hasn't the parameter.
    static String queryParam(final String rawQuery, final String name) {
        if (rawQuery == null) {
            return null;
        }
        for (final String pair : rawQuery.split("&")) {
            final int equals = pair.indexOf('=');
            final String key = equals >= 0
                    ? pair.substring(0, equals)
                    : pair;
            if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
                return equals >= 0
                        ? URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8)
                        : "";
            }
        }
        return null;
    }

    private static void sendErrorIfPossible(final HttpExchange exchange) {
        // Once the headers have gone the status can't be changed
        if (exchange.getResponseCode() == -1) {
            try {
                exchange.sendResponseHeaders(500, -1);
            } catch (final IOException | RuntimeException sendError) {
                LOGGER.debug("Unable to send the error for {}", exchange.getRequestURI(), sendError);
            }
        }
    }
}
