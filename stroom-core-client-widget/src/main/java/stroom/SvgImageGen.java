package stroom;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        Path imagesPath = appPath.resolve("src").resolve("main").resolve("resources").resolve("ui").resolve("images");

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
                            if (!existing.equals(xml)) {
                                System.out.println("Clashing name: " + fileName);
                            }
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

        final StringBuilder sb = new StringBuilder();
        sb.append("package stroom.widget.tab.client.view;\n\n");
        sb.append("public class SvgImage {\n\n");
        entries.keySet().stream().sorted().forEach(key -> {
            sb.append("    public static String ");
            sb.append(key);
            sb.append(" = \"\" +\n");

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
            sb.append("            \"\";\n\n");
        });

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

        final Path outPath = widgetPath.resolve("src/main/java/stroom/widget/tab/client/view/SvgImage.java");
        try (final OutputStream outputStream = Files.newOutputStream(outPath)) {
            outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
