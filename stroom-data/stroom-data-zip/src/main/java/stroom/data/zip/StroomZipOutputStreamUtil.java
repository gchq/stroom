package stroom.data.zip;


import stroom.util.io.CloseableUtil;

import java.io.IOException;
import java.io.OutputStream;

public class StroomZipOutputStreamUtil {
    public static void addSimpleEntry(StroomZipOutputStream stroomZipOutputStream, StroomZipEntry entry, byte[] data)
            throws IOException {
        OutputStream outputStream = null;
        try {
            outputStream = stroomZipOutputStream.addEntry(entry.getFullName());
            outputStream.write(data);
        } finally {
            CloseableUtil.close(outputStream);
        }
    }
}
