package stroom.feed;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Map that does not care about key case.
 */
public class MetaMap extends CIStringHashMap {
    private static final long serialVersionUID = 4877407570072403322L;

    public final static String NAME = "metaMap";
    private static final String HEADER_DELIMITER = ":";
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    public void read(final InputStream inputStream, final boolean close) throws IOException {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, DEFAULT_CHARSET));

        String line;
        while ((line = reader.readLine()) != null) {
            final int splitPos = line.indexOf(HEADER_DELIMITER);
            if (splitPos != -1) {
                final String key = line.substring(0, splitPos);
                final String value = line.substring(splitPos + 1);
                put(key, value);
            } else {
                put(line.trim(), null);
            }
        }

        if (close) {
            inputStream.close();
        }
    }

    public void read(final byte[] data) throws IOException {
        read(new ByteArrayInputStream(data), true);
    }

    public void write(final OutputStream outputStream, final boolean close) throws IOException {
        write(new OutputStreamWriter(outputStream, DEFAULT_CHARSET), close);
    }

    public void write(final Writer writer, final boolean close) throws IOException {
        try {
            final List<CIString> sortedKeys = new ArrayList<>(realMap.keySet());
            Collections.sort(sortedKeys);
            for (final CIString key : sortedKeys) {
                writer.write(key.getKey());
                final String value = realMap.get(key);
                if (value != null) {
                    writer.write(":");
                    writer.write(value);
                }
                writer.write("\n");
            }
        } finally {
            if (close) {
                writer.close();
            } else {
                writer.flush();
            }
        }
    }

    public byte[] toByteArray() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        write(byteArrayOutputStream, true);
        return byteArrayOutputStream.toByteArray();
    }

    public void removeAll(final Collection<String> keySet) {
        for (final String key : keySet) {
            remove(key);
        }
    }
}
