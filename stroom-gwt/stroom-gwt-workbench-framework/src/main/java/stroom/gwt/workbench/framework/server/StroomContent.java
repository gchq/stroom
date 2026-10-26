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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// The visualisations and scripts in directories of Stroom content, as a content pack's source
/// holds them (e.g. `stroom-visualisations-dev`'s `war/stroom_content`), so that the workbench can
/// show Stroom's dashboards with real visualisations.
///
/// Each document is a set of files named `<name>.<type>.<uuid>.<extension>`: a `.meta` file of
/// JSON describing it, and its data, e.g. a Script's `.js` and a Visualisation's settings, `.json`.
/// A document in Stroom's V2 format (e.g. the content pack the visualisations' build makes) may
/// also have path assets, files in a directory beside its own named
/// `<name>.<type>.<uuid>-path-assets`, which are served as Stroom's `DocumentAssetServlet` serves
/// them, see [#findAsset(String, String)]. An asset-based visualisation's are its `index.html`, its
/// code and its CSS.
///
/// The directories are read again for each request, so that changes to the content show on the
/// next page load.
public class StroomContent {

    /// The path of the scripts a visualisation's frame (`/ui/vis.html`) loads, as Stroom's
    /// `ScriptServlet` serves them: `/ui/script?uuid=<uuid>&name=<name>&ver=<version>`.
    public static final String SCRIPT_PATH = "/ui/script";

    /// The path of the visualisations and scripts as JSON, see [#visualisationsJson()].
    public static final String VISUALISATIONS_PATH = "/__workbench/content/visualisations";

    /// The path under which documents' path assets are served, as `/assets/<uuid>/<path>`.
    public static final String ASSETS_PATH = "/assets/";

    private static final String PATH_ASSETS_SUFFIX = "-path-assets";
    private static final String INDEX_ASSET = "index.html";

    private static final String META = "meta";
    private static final String SCRIPT = "Script";
    private static final String VISUALISATION = "Visualisation";

    private final List<Path> roots;

    /// @param roots The directories of content, searched in order.
    public StroomContent(final List<Path> roots) {
        Objects.requireNonNull(roots);
        this.roots = roots.stream()
                .map(root -> root.toAbsolutePath().normalize())
                .toList();
    }

    /// @return The directories of content.
    public List<Path> getRoots() {
        return roots;
    }

    /// Finds a script's JavaScript.
    ///
    /// @param uuid The script's UUID.
    /// @return The `.js` file of the script with the UUID, or empty if there is none.
    public Optional<Path> findScript(final String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        return documentFiles()
                .filter(file -> file.type().equals(SCRIPT)
                                && file.uuid().equals(uuid)
                                && file.extension().equals("js"))
                .map(DocumentFile::path)
                .findFirst();
    }

    /// Finds one of a document's path assets.
    ///
    /// @param uuid      The document's UUID.
    /// @param assetPath The asset's path among the document's assets, e.g. `index.html` or
    ///                  `lib/d3.js`.
    /// @return The asset's file, or empty if the document has no such asset (or the path would
    /// leave its assets' directory).
    public Optional<Path> findAsset(final String uuid, final String assetPath) {
        if (uuid == null || uuid.isBlank() || assetPath == null || assetPath.isBlank()) {
            return Optional.empty();
        }
        for (final Path directory : assetDirectories(uuid).toList()) {
            final Path file = directory.resolve(assetPath);
            if (FilePaths.isRegularFileWithin(file, directory)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    /// The visualisations and scripts, as JSON:
    /// ```
    /// {"visualisations": [{"path": "<its .meta>", "meta": "<its .meta's text>",
    ///                      "settings": "<its .json's text>", "indexAsset": <true or false>}, ...],
    ///  "scripts": [{"path": "<its .meta>", "meta": "<its .meta's text>"}, ...]}
    /// ```
    /// The files' text is given as it is, not parsed, so that one with a mistake (a content pack
    /// may have an empty or broken `.meta`) can be reported and skipped by the reader rather than
    /// spoiling the rest. A path is relative to its content directory. A visualisation without a
    /// `.json` file has the settings `null`, and `indexAsset` says whether it is asset-based, i.e.
    /// has an `index.html` path asset. Each list is in the order of the files' paths.
    ///
    /// @return The JSON.
    public String visualisationsJson() {
        final List<DocumentFile> metas = documentFiles()
                .filter(file -> file.extension().equals(META))
                .sorted(Comparator.comparing(DocumentFile::path))
                .toList();
        final List<String> visualisations = new ArrayList<>();
        final List<String> scripts = new ArrayList<>();
        for (final DocumentFile meta : metas) {
            final String common = "{\"path\":" + Json.quote(relativePath(meta.path())) + ",\"meta\":"
                                  + Json.quote(read(meta.path()));
            if (meta.type().equals(VISUALISATION)) {
                final Path settings = meta.sibling("json");
                visualisations.add(common + ",\"settings\":" + (Files.isRegularFile(settings)
                        ? Json.quote(read(settings))
                        : "null")
                                   + ",\"indexAsset\":" + findAsset(meta.uuid(), INDEX_ASSET).isPresent()
                                   + "}");
            } else if (meta.type().equals(SCRIPT)) {
                scripts.add(common + "}");
            }
        }
        return "{\"visualisations\":[" + String.join(",", visualisations) + "],\"scripts\":["
               + String.join(",", scripts) + "]}";
    }

    // Every file named as a document's in the roots, those in earlier roots first
    private Stream<DocumentFile> documentFiles() {
        return roots.stream()
                .filter(Files::isDirectory)
                .flatMap(StroomContent::documentFiles);
    }

    private static Stream<DocumentFile> documentFiles(final Path root) {
        try (final Stream<Path> paths = Files.walk(root)) {
            // Collected, so that the walk is closed before the files are used. A document's path
            // assets aren't documents, whatever they are called
            return paths.filter(path -> FilePaths.isRegularFileWithin(path, root))
                    .filter(path -> !isInPathAssets(root.relativize(path)))
                    .map(DocumentFile::parse)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList())
                    .stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isInPathAssets(final Path relativePath) {
        for (final Path part : relativePath) {
            if (part.toString().endsWith(PATH_ASSETS_SUFFIX)) {
                return true;
            }
        }
        return false;
    }

    // The directories of the path assets of the document with a UUID, those in earlier roots first
    private Stream<Path> assetDirectories(final String uuid) {
        final String suffix = "." + uuid + PATH_ASSETS_SUFFIX;
        return roots.stream()
                .filter(Files::isDirectory)
                .flatMap(root -> {
                    try (final Stream<Path> paths = Files.walk(root)) {
                        return paths.filter(Files::isDirectory)
                                .filter(path -> path.getFileName().toString().endsWith(suffix))
                                .collect(Collectors.toList())
                                .stream();
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    // A file's path relative to the root it is in, with forward slashes
    private String relativePath(final Path path) {
        for (final Path root : roots) {
            if (path.startsWith(root)) {
                return root.relativize(path).toString().replace('\\', '/');
            }
        }
        return path.getFileName().toString();
    }

    private static String read(final Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    // --------------------------------------------------------------------------------


    // A file of a document, named <name>.<type>.<uuid>.<extension> (the name may hold dots)
    private record DocumentFile(Path path, String name, String type, String uuid, String extension) {

        static Optional<DocumentFile> parse(final Path path) {
            final String fileName = path.getFileName().toString();
            final int extensionDot = fileName.lastIndexOf('.');
            final int uuidDot = extensionDot > 0
                    ? fileName.lastIndexOf('.', extensionDot - 1)
                    : -1;
            final int typeDot = uuidDot > 0
                    ? fileName.lastIndexOf('.', uuidDot - 1)
                    : -1;
            if (typeDot <= 0) {
                return Optional.empty();
            }
            return Optional.of(new DocumentFile(path,
                    fileName.substring(0, typeDot),
                    fileName.substring(typeDot + 1, uuidDot),
                    fileName.substring(uuidDot + 1, extensionDot),
                    fileName.substring(extensionDot + 1)));
        }

        // The document's file with another extension
        Path sibling(final String otherExtension) {
            return path.resolveSibling(name + "." + type + "." + uuid + "." + otherExtension);
        }
    }
}
