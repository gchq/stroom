package stroom.util.logging;

import stroom.util.date.DateUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class TempTagCloudDebug {
    private static final Writer writer;

    static {
        Writer w;
        try {
            w = Files.newBufferedWriter(
                    Paths.get("/home/stroomdev66/work/stroom-master-temp3/tag_cloud_debug_" +
                            DateUtil.createFileDateTimeString() +
                            ".txt"), StandardOpenOption.CREATE_NEW);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        writer = w;
        write("======= TempTagCloudDebug =======");

        try {
            writer.write("\n");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TempTagCloudDebug() {
    }

    public static synchronized void printStackLine() {
        try {
            throw new RuntimeException();
        } catch (final RuntimeException e) {
            try {
                int count = 0;
                for (final StackTraceElement el : e.getStackTrace()) {
                    if (!el.getClassName().contains(TempTagCloudDebug.class.getName())) {
                        writer.write("\t\t\t\t\t\t");
                        writer.write(el.getClassName());
                        writer.write(".");
                        writer.write(el.getMethodName());

                        writer.write(":");
                        writer.write(String.valueOf(el.getLineNumber()));
                        writer.write("\n");

                        count++;
//                        if (count >= 100) {
//                            break;
//                        }
                    }
                }
            } catch (final IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }

    public static synchronized void write(final String line) {
//        try {
//            writer.write(line);
//            writer.write("\n");
//            printStackLine();
//            writer.flush();
//
//        } catch (final IOException e) {
//            throw new UncheckedIOException(e);
//        }
    }
}
