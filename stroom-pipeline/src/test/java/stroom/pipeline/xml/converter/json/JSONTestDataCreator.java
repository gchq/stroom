package stroom.pipeline.xml.converter.json;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class JSONTestDataCreator {

    void writeFile(final Path path, final JsonType[][] structure) throws IOException {
        try (final Writer writer = Files.newBufferedWriter(path)) {
            final JsonType[] level = structure[0];
            for (int i = 0; i < level.length; i++) {
                final JsonType type = level[i];
                writeType(writer, structure, 0, type, i + 1);
                writer.write("\n");
            }
        }
    }

    private void writeType(final Writer writer,
                           final JsonType[][] structure,
                           final int depth,
                           final JsonType type,
                           final int no) throws IOException {
        switch (type) {
            case OBJECT -> writeJsonObject(writer, structure, depth);
            case ARRAY -> writeJsonArray(writer, structure, depth);
            case VALUE -> writer.write("\"value" + no + "\"");
        }
    }

    private void writeJsonArray(final Writer writer,
                                final JsonType[][] structure,
                                final int depth) throws IOException {
        writer.write("[\n");
        final int childDepth = depth + 1;
        if (childDepth < structure.length) {
            final JsonType[] types = structure[childDepth];
            for (int i = 0; i < types.length; i++) {
                writeIndent(writer, childDepth);

                writeType(writer, structure, childDepth, types[i], i + 1);

                if (i + 1 < types.length) {
                    writer.write(",");
                }
                writer.write("\n");
            }
        }
        writeIndent(writer, depth);
        writer.write("]");
    }

    private void writeJsonObject(final Writer writer,
                                 final JsonType[][] structure,
                                 final int depth) throws IOException {
        writer.write("{\n");
        final int childDepth = depth + 1;
        if (childDepth < structure.length) {
            final JsonType[] types = structure[childDepth];
            for (int i = 0; i < types.length; i++) {
                writeIndent(writer, childDepth);

                writer.write("\"key" + (i + 1) + "\" : ");
                writeType(writer, structure, childDepth, types[i], i + 1);

                if (i + 1 < types.length) {
                    writer.write(",");
                }
                writer.write("\n");
            }
        }
        writeIndent(writer, depth);
        writer.write("}");
    }

    private void writeIndent(final Writer writer, final int depth) throws IOException {
        for (int i = 0; i < depth; i++) {
            writer.write("   ");
        }
    }
}
