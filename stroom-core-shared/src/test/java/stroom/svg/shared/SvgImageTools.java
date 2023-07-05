package stroom.svg.shared;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SvgImageTools {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SvgImageTools.class);

//    private static final Pattern HEX_COLOUR_PATTERN = Pattern.compile("#[0-9a-fA-F]+");
    private static final Pattern HEX_COLOUR_PATTERN = Pattern.compile("(?<=(fill|stroke):)#[0-9a-fA-F]+");
    private static final String HTML_HEADER = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <link rel="stylesheet" href="svg-image-tools.css">
            </head>
            <!-- Google thinks this is danish -->
            <meta name="google" content="notranslate">
            """;
    private static final Set<Path> THIRD_PARTY_SVGS = Set.of(
            Paths.get("document", "ElasticCluster.svg"),
            Paths.get("document", "ElasticIndex.svg"),
            Paths.get("document", "SolrIndex.svg"),
            Paths.get("pipeline", "elastic_index.svg"),
            Paths.get("pipeline", "hadoop.svg"),
            Paths.get("pipeline", "solr.svg")
    );

    @Disabled // Not a test, manual running only
    @Test
    void listUniqueSvgColours() throws IOException {
        final Path imagesSourceBasePath = getImagesSourceBasePath();
        final Path outputPath = getOutputPath();

        try (Stream<Path> fileStream = Files.walk(imagesSourceBasePath)) {
            final Map<String, Set<ColourInstance>> colourToFilesMap = fileStream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".svg"))
                    .filter(path -> !THIRD_PARTY_SVGS.contains(imagesSourceBasePath.relativize(path)))
                    .flatMap(svgFile -> {
                        try {
                            final String svgStr = Files.readString(svgFile);
                            return HEX_COLOUR_PATTERN.matcher(svgStr)
                                    .results()
                                    .map(MatchResult::group)
                                    .map(match -> new ColourInstance(
                                            svgFile,
                                            match));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.groupingBy(ColourInstance::colour, Collectors.toSet()));

            colourToFilesMap.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .map(entry ->
                            padColour(entry.getKey())
                                    + " - "
                                    + entry.getValue()
                                    .stream()
                                    .map(ColourInstance::svgFile)
                                    .sorted()
                                    .map(file -> imagesSourceBasePath.relativize(file).toString())
                                    .collect(Collectors.joining(", "))
                    )
                    .forEach(System.out::println);

            final String rowsHtml = colourToFilesMap.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
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
                                                imagesSourceBasePath.relativize(path).toString());
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                                .collect(Collectors.joining("\n"));

                        return LogUtil.message("""
                                        <div class="row">
                                          <div class="colour-swatch"
                                               style="background-color:{};"
                                               title="{}" ></div>
                                          <div class="colour-hex">{}</div>
                                          <div class="file-list">
                                            {}
                                          </div>
                                        </div>""",
                                colourHex,
                                colourHex,
                                colourHex,
                                fileHtml);
                    })
                    .collect(Collectors.joining("\n"));

            final String html = LogUtil.message("""
                            {}
                            <div class="container">
                              {}
                            </div>
                            """,
                    HTML_HEADER,
                    rowsHtml);

            Files.writeString(outputPath.resolve("colourSwatches.html"), html);
        }
    }

    private String padColour(final String colour) {
        if (colour.length() < 7) {
            final int diff = 7 - colour.length();
            final StringBuilder sb = new StringBuilder(7)
                    .append(colour);
            for (int i = 0; i < diff; i++) {
                sb.append(" ");
            }
            return sb.toString();
        } else {
            return colour;
        }
    }

    private static Path getImagesSourceBasePath() {
        final Path coreSharedPath = getCoreSharedPath();
        final Path appPath = SvgImageGen.getAppPath(coreSharedPath);
        final Path imagesSourceBasePath = SvgImageGen.getImagesSourceBasePath(appPath);
        LOGGER.info("Scanning dir: {}", imagesSourceBasePath.toAbsolutePath().normalize());
        return imagesSourceBasePath;
    }

    private static Path getOutputPath() {
        final Path coreSharedPath = getCoreSharedPath();
        return coreSharedPath.resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve(SvgImageTools.class.getSimpleName());
    }

    private static Path getCoreSharedPath() {
        final Path coreSharedPath = Paths.get(".").toAbsolutePath().normalize();
        return coreSharedPath;
    }


    // --------------------------------------------------------------------------------


    private record ColourInstance(
            Path svgFile,
            String colour) {

    }
}
