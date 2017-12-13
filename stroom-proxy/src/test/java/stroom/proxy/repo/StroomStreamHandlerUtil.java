package stroom.proxy.repo;

import stroom.util.io.CloseableUtil;

import java.io.IOException;
import java.io.OutputStream;

public final class StroomStreamHandlerUtil {
    private StroomStreamHandlerUtil() {
        // Utlity class.
    }

    public static StroomStreamHandler createStroomStreamHandler(final StroomZipOutputStream stroomZipOutputStream) {
        return new StroomStreamHandler() {
            private OutputStream outputStream;

            @Override
            public void handleEntryStart(StroomZipEntry stroomZipEntry) throws IOException {
                outputStream = stroomZipOutputStream.addEntry(stroomZipEntry.getFullName());
            }

            @Override
            public void handleEntryEnd() throws IOException {
                CloseableUtil.close(outputStream);
            }

            @Override
            public void handleEntryData(byte[] data, int off, int len) throws IOException {
                if (outputStream != null) {
                    outputStream.write(data, off, len);
                }
            }
        };
    }

    public static StroomStreamHandler createStroomStreamOrderCheck() {
        final StroomZipNameSet stroomZipNameSet = new StroomZipNameSet(true);
        return new StroomStreamHandler() {
            @Override
            public void handleEntryStart(StroomZipEntry stroomZipEntry) throws IOException {
                stroomZipNameSet.add(stroomZipEntry.getFullName());
            }

            @Override
            public void handleEntryEnd() throws IOException {
            }

            @Override
            public void handleEntryData(byte[] data, int off, int len) throws IOException {
            }
        };
    }
}

