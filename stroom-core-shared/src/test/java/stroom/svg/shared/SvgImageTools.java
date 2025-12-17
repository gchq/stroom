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

package stroom.svg.shared;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SvgImageTools {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SvgImageTools.class);

    //    private static final Pattern HEX_COLOUR_PATTERN = Pattern.compile("#[0-9a-fA-F]+");
    private static final Pattern HEX_COLOUR_PATTERN = Pattern.compile("(?<=(fill|stroke):)#[0-9a-fA-F]+");
    private static final String COLOURS_HTML_HEADER = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <link rel="stylesheet" href="colour-swatches.css">
            </head>
            <!-- Google thinks this is danish -->
            <meta name="google" content="notranslate">
            """;

    private static final String THEMES_HTML_HEADER = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <link rel="stylesheet" href="themed-icons.css">
              <link rel="stylesheet" href="css-symlink/app.css">
              <script type="text/javascript" src="themed-icons.js"></script>
            </head>
            <!-- Google thinks this is danish -->
            <meta name="google" content="notranslate">
            """;

    private static final List<String> BACKGROUND_VARIABLES = List.of(
            "--app__background-color",
            "--row__background-color--even",
            "--row__background-color--odd",
            "--row__background-color--hovered",
            "--row__background-color--selected",
            "--row__background-color--selected--hovered",
            "--row__background-color--focussed",
            "--row__background-color--selected--focussed",

            "--pipeline-element-box__background-color",
            "--pipeline-element-box__background-color--hover",
            "--pipeline-element-box__background-color--selected",
            "--pipeline-element-box__background-color--selected--hover",
            "--pipeline-element-box__background-color--hotspot"
    );

    private static final Set<Path> THIRD_PARTY_SVGS = Set.of(
            Paths.get("document", "ElasticCluster.svg"),
//            Paths.get("document", "ElasticIndex.svg"),
            Paths.get("document", "SolrIndex.svg"),
            Paths.get("pipeline", "elastic_index.svg"),
            Paths.get("pipeline", "hadoop.svg"),
            Paths.get("pipeline", "solr.svg")
    );
    public static final String THEMED_ICONS_HTML_FILENAME = "themedIcons.html";
    public static final String COLOUR_SWATCHES_FILENAME = "colourSwatches.html";

    public static void main(final String[] args) throws IOException {
        generateUniqueColoursContactSheet();
        generateThemedIconsContactSheet();
    }

    static void generateUniqueColoursContactSheet() throws IOException {
        final Path rawImagesBasePath = getRawImagesBasePath();
        final Path outputPath = getOutputPath();

        try (final Stream<Path> fileStream = Files.walk(rawImagesBasePath)) {
            final Map<String, Set<ColourInstance>> colourToFilesMap = fileStream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".svg"))
                    .filter(path -> !THIRD_PARTY_SVGS.contains(rawImagesBasePath.relativize(path)))
                    .flatMap(svgFile -> {
                        try {
                            final String svgStr = Files.readString(svgFile);
                            return HEX_COLOUR_PATTERN.matcher(svgStr)
                                    .results()
                                    .map(MatchResult::group)
                                    .map(colourHex -> new ColourInstance(
                                            svgFile,
                                            colourHex.toLowerCase()));
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.groupingBy(ColourInstance::colour, Collectors.toSet()));

            // Compare colours roughly based on dark -> light
            final Comparator<Entry<String, ?>> hexColourComparator = (o1, o2) -> {
                // For some reason IJ adds java.awt.* import which breaks checkstyle
                final java.awt.Color colour1 = java.awt.Color.decode(toLongForm(o1.getKey()));
                final java.awt.Color colour2 = java.awt.Color.decode(toLongForm(o2.getKey()));
                return Integer.compare(
                        colour1.getRed() + colour1.getGreen() + colour1.getBlue(),
                        colour2.getRed() + colour2.getGreen() + colour2.getBlue());
            };

            final String rowsHtml = colourToFilesMap.entrySet()
                    .stream()
                    .sorted(hexColourComparator)
                    .map(entry -> {
                        final String colourHex = entry.getKey();
                        final String fileHtml = entry.getValue().stream()
                                .map(ColourInstance::svgFile)
                                .sorted()
                                .map(path -> {
                                    try {
                                        final String svg = Files.readString(path);
                                        return LogUtil.message("""
                                                        <div class="svg-file">
                                                          <div class="svg">{}</div>
                                                          <div class="file-path">{}</div>
                                                        </div>""",
                                                svg,
                                                rawImagesBasePath.relativize(path).toString());
                                    } catch (final IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .collect(Collectors.joining("\n"));

                        final String colourClass = Objects.requireNonNullElse(
                                        GenerateSvgImages.COLOUR_MAPPINGS.get(colourHex),
                                        "")
                                .replace("var(", "")
                                .replace(")", "");

                        return LogUtil.message("""
                                        <div class="row">
                                          <div class="colour-swatch"
                                               style="background-color:{};"
                                               title="{}" ></div>
                                          <div class="colour-hex">{}</div>
                                          <div class="colour-class">{}</div>
                                          <div class="file-list">
                                            {}
                                          </div>
                                        </div>""",
                                colourHex,
                                colourHex,
                                colourHex,
                                colourClass,
                                fileHtml);
                    })
                    .collect(Collectors.joining("\n"));

            final String html = LogUtil.message("""
                            {}
                            <div class="container">
                              {}
                            </div>
                            """,
                    COLOURS_HTML_HEADER,
                    rowsHtml);

            Files.writeString(outputPath.resolve(COLOUR_SWATCHES_FILENAME), html);
        }
    }

    static void generateThemedIconsContactSheet() throws IOException {
        final Path generatedImagesBasePath = getGeneratedImagesBasePath();
        final Path outputPath = getOutputPath();

        try (final Stream<Path> fileStream = Files.walk(generatedImagesBasePath)) {
            final String mainHtml = fileStream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".svg"))
                    .sorted()
                    .map(svgFile -> {
                        final Path relPath = generatedImagesBasePath.relativize(svgFile);
//                        LOGGER.debug("{}", svgFile);
                        try {
                            final String svgStr = Files.readString(svgFile);

                            return LogUtil.message("""
                                    <div class="themed-icons-row svg-pair">
                                      <div class="svg-name">{}</div>
                                      <div class="themed-icons-panel stroom-theme-light">
                                        <div class="svgIcon themed-icons-svg svg-image svg-light">{}</div>
                                      </div>
                                      <div class="themed-icons-panel stroom-theme-dark">
                                        <div class="svgIcon themed-icons-svg svg-image svg-dark">{}</div>
                                      </div>
                                    </div>""", relPath, svgStr, svgStr);

                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.joining("\n"));

            final String optionsHtml = BACKGROUND_VARIABLES.stream()
                    .map(var -> LogUtil.message("""
                                    <option value="var({})">{}</option>""",
                            var, var))
                    .collect(Collectors.joining("\n"));

            @SuppressWarnings("checkstyle:LineLength") final String html = LogUtil.message("""
                            {}
                            <body>
                              <div class="themed-icons-toolbar">
                                <label for="variables">Choose a background:</label>
                                <select name="variables" id="variables" onchange="onBackgroundVariableChange()" onkeyup="onBackgroundVariableChange()" >
                                  {}
                                </select>
                              </div>
                              <div class="themed-icons-container max">
                                {}
                              </div>
                            </body>
                            """,
                    THEMES_HTML_HEADER,
                    optionsHtml,
                    mainHtml);

            Files.writeString(outputPath.resolve(THEMED_ICONS_HTML_FILENAME), html);
        }
        System.out.println(LogUtil.message("""

                        To view the icon contact sheets run:
                          python -m http.server 8888 --directory {}
                        Then visit
                          http://localhost:8888/{}
                          http://localhost:8888/{}
                        """,
                outputPath.toAbsolutePath().normalize(),
                THEMED_ICONS_HTML_FILENAME,
                COLOUR_SWATCHES_FILENAME));
    }

    private static String padColour(final String colour) {
        if (colour.length() < 7) {
            final int diff = 7 - colour.length();
            return colour + " ".repeat(diff);
        } else {
            return colour;
        }
    }

    private static Path getRawImagesBasePath() {
        final Path coreSharedPath = GenerateSvgImages.getCoreSharedPath();
        final Path appPath = GenerateSvgImages.getAppPath(coreSharedPath);
        final Path imagesSourceBasePath = GenerateSvgImages.getImagesSourceBasePath(appPath);
        LOGGER.debug("Scanning dir: {}", imagesSourceBasePath.toAbsolutePath().normalize());
        return imagesSourceBasePath;
    }

    private static Path getGeneratedImagesBasePath() {
        final Path coreSharedPath = GenerateSvgImages.getCoreSharedPath();
        final Path appPath = GenerateSvgImages.getAppPath(coreSharedPath);
        final Path imagesDestBasePath = GenerateSvgImages.getImagesDestBasePath(appPath);
        LOGGER.debug("Scanning dir: {}", imagesDestBasePath.toAbsolutePath().normalize());
        return imagesDestBasePath;
    }

    private static Path getOutputPath() {
        final Path coreSharedPath = GenerateSvgImages.getCoreSharedPath();
        final Path path = coreSharedPath.resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve(SvgImageTools.class.getSimpleName());
        LOGGER.debug("Using output path: " + path.toAbsolutePath().normalize());
        return path;
    }

    private static Path getCoreSharedPath() {
        final Path coreSharedPath = Paths.get(".").toAbsolutePath().normalize();
        return coreSharedPath;
    }

    private static String toLongForm(final String colourHex) {
        if (colourHex.length() == 4) {
            return
                    "#"
                    + colourHex.charAt(1) + colourHex.charAt(1)
                    + colourHex.charAt(2) + colourHex.charAt(2)
                    + colourHex.charAt(3) + colourHex.charAt(3);
        } else {
            return colourHex;
        }
    }


    // --------------------------------------------------------------------------------


    private record ColourInstance(
            Path svgFile,
            String colour) {

    }
}
