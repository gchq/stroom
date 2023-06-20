package stroom;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class SvgImageGen {

    private static final String WIDGET_DIR = "stroom-core-client-widget";
    private static final String APP_DIR = "stroom-app";

    public static void main(final String[] args) {

        Path widgetPath = Paths.get(".").resolve(WIDGET_DIR).toAbsolutePath();
        while (!widgetPath.getFileName().toString().equals(WIDGET_DIR)) {
            widgetPath = widgetPath.getParent();
        }

        Path appPath = widgetPath.getParent().resolve(APP_DIR);

        Path rawImagesPath = appPath.resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("ui")
                .resolve("raw-images");

        Path imagesPath = appPath.resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("ui")
                .resolve("images");

        // First transform the raw images.
        deleteDirectory(imagesPath);
        try (final Stream<Path> stream = Files.walk(rawImagesPath)) {
            stream.forEach(f -> {
                try {
                    String fileName = f.getFileName().toString();
                    if (fileName.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                        final Path relative = rawImagesPath.relativize(f);

                        final StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < relative.getNameCount(); i++) {
                            if (sb.length() > 0) {
                                sb.append("_");
                            }
                            sb.append(relative.getName(i));
                        }
                        final String formatted = formatFileName(sb.toString());
                        final Path output = imagesPath.resolve(formatted);
                        Files.createDirectories(output.getParent());

                        String xml = Files.readString(f, StandardCharsets.UTF_8);
                        xml = xml.replaceAll("#000000", "var(--icon-colour__foreground)");
                        xml = xml.replaceAll("#2196f4", "var(--icon-colour__xsd-background)");
                        xml = xml.replaceAll("#aed581", "var(--icon-colour__xsl-background)");
                        xml = xml.replaceAll("#ce93d8", "var(--icon-colour__xml-background)");
                        xml = xml.replaceAll("#4A4B4C", "var(--icon-colour__grey)");
                        xml = xml.replaceAll("#010101", "currentColor");

                        if (Files.exists(output)) {
                            System.err.println("File exists: " + output);
                        } else {
                            Files.writeString(output, xml);
                        }
                    } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
                        final Path relative = rawImagesPath.relativize(f);
                        final Path output = imagesPath.resolve(relative);
                        Files.createDirectories(output.getParent());

                        if (Files.exists(output)) {
                            System.err.println("File exists: " + output);
                        } else {
                            Files.copy(f, output);
                        }
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }


        final Map<String, String> entries = new HashMap<>();
        try (final Stream<Path> stream = Files.walk(imagesPath)) {
            stream.forEach(f -> {
                try {
                    String fileName = f.getFileName().toString();
                    if (fileName.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                        final String xml = Files.readString(f, StandardCharsets.UTF_8);
                        fileName = fileName.substring(0, fileName.length() - 4);
                        fileName = fileName.replaceAll("([a-z])([A-Z])", "$1_$2");
                        fileName = fileName.toUpperCase(Locale.ROOT);
                        fileName = fileName.replaceAll("[^A-Z0-9]", "_");
                        fileName = fileName.replaceAll("_+", "_");
                        fileName = fileName.replaceAll("^_+", "");
                        fileName = fileName.replaceAll("_+$", "");

                        final String existing = entries.get(fileName);
                        if (existing != null) {
                            System.out.println("Clashing name: " + fileName);
                        } else {
                            entries.put(fileName, xml);
                        }
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final StringBuilder sb = new StringBuilder()
                .append("package stroom.svg.client;\n\n")
                .append("@SuppressWarnings({\"ConcatenationWithEmptyString\", \"TextBlockMigration\", \"unused\"})\n")
                .append("public enum SvgImage {\n\n")
                .append("    // ================================================================================\n")
                .append("    // IMPORTANT - This class is generated by ")
                .append(SvgImageGen.class.getName())
                .append("\n")
                .append("    // ================================================================================\n")
                .append("\n");

        entries.keySet().stream().sorted().forEach(key -> {
//            sb.append("    public static String ");
            sb.append("    ");
            sb.append(key);
            sb.append("(\"\" +\n");

            String xml = entries.get(key);

            final String[] parts = xml.split("\n");
            for (String part : parts) {
                if (part.length() == 0) {
                    sb.append("            \"\\n\" +\n");
                } else {
                    while (part.length() > 0) {
                        int size = Math.min(80, part.length());
                        String line = part.substring(0, size);
                        part = part.substring(size);
                        line = line.replaceAll("\"", "\\\\\"");
                        line = line.replaceAll("\t", " ");
                        sb.append("            \"");
                        sb.append(line);
                        if (part.length() > 0) {
                            sb.append("\" +\n");
                        }
                    }
                    sb.append("\\n\" +\n");
                }
            }
            sb.append("            \"\"),\n\n");
        });

        sb.replace(sb.length() - 3, sb.length() - 1, ";\n\n");

        sb.append("    private final String svg;\n\n")
                .append("    SvgImage(final String svg) {\n")
                .append("        this.svg = svg;\n")
                .append("    }\n")
                .append("\n")
                .append("    public String getSvg() {\n")
                .append("        return svg;\n")
                .append("    }\n")
                .append("\n")
                .append("    public String getCssClass() {\n")
                .append("        return \"svg-image-\" + name().toLowerCase().replace(\"_\", \"-\");\n")
                .append("    }\n");

//        for (final Entry<String, String> entry : entries.entrySet()) {
//            sb.append("    public static String ");
//            sb.append(entry.getKey());
//            sb.append(" = \"\" +\n");
//
//            String xml = entry.getValue();
//            while (xml.length() > 0) {
//                int size = Math.min(80, xml.length());
//                String line = xml.substring(0, size);
//                line = line.replaceAll("\"", "\\\\\"");
//                line = line.replaceAll("\n", "\\\\n");
//                sb.append("            \"");
//                sb.append(line);
//                sb.append("\" +\n");
//                xml = xml.substring(size);
//            }
//            sb.append("            \"\";\n\n");
//        }
        sb.append("}\n");

        final Path outPath = widgetPath.resolve("src/main/java/stroom/svg/client/SvgImage.java");
        try (final OutputStream outputStream = Files.newOutputStream(outPath)) {
            outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void deleteDirectory(Path directoryToBeDeleted) {
        try {
            if (Files.isDirectory(directoryToBeDeleted)) {
                Files.walk(directoryToBeDeleted)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String formatFileName(String fileName) {
        fileName = fileName.replaceAll("([a-z])([A-Z])", "$1_$2");
        fileName = fileName.toLowerCase(Locale.ROOT);
        fileName = fileName.replaceAll("[^a-z0-9.]", "_");
        fileName = fileName.replaceAll("_+", "_");
        fileName = fileName.replaceAll("^_+", "");
        fileName = fileName.replaceAll("_+$", "");
        return fileName;
    }
}
