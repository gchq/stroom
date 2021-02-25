package stroom.pipeline.refdata.util;

import stroom.util.date.DateUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class TempBasicLmdbDebug {
    private static Writer writer;

    private TempBasicLmdbDebug() {
    }

    public static synchronized void printStackLine() {
        try {
            throw new RuntimeException();
        } catch (final RuntimeException e) {
            try {
                final Writer writer = getWriter();
                for (final StackTraceElement el : e.getStackTrace()) {
                    if (!el.getClassName().contains(TempBasicLmdbDebug.class.getName())) {
                        writer.write(el.getClassName());
                        writer.write(".");
                        writer.write(el.getMethodName());
                        writer.write(":");
                        writer.write(String.valueOf(el.getLineNumber()));
                        writer.write(" < ");
                    }
                }
            } catch (final IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    public static synchronized void write(final String line) {
//        try {
//            final Writer writer = getWriter();
//            writer.write(line);
//            writer.write("\t\t\t");
//            printStackLine();
//            writer.write("\n");
//            writer.flush();
//
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
    }

    private static Writer getWriter() {
        if (writer == null) {
            final String dateTime  = DateUtil.createFileDateTimeString();

            Writer w;
            try {
                final Path path =
                        Paths.get("/home/stroomdev66/work/stroom-master-temp3/TempBasicLmdbDebug_" +
                                dateTime +
                                ".txt");
                if (Files.isRegularFile(path)) {
                    w = Files.newBufferedWriter(path, StandardOpenOption.APPEND);
                } else {
                    w = new FileWriter(path.toFile());
//                    w = Files.newBufferedWriter(path);
                }

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            writer = w;
            write("======= TempBasicLmdbDebug " + dateTime  + "=======");

            try {
                writer.write("\n");
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return writer;
    }
}
