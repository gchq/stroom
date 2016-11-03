/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.zip;

import stroom.util.io.CloseableUtil;
import stroom.util.io.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Map that does not care about key case.
 */
public class HeaderMap implements Serializable, Map<String, String> {
    private static final long serialVersionUID = 4877407570072403322L;

    public static final String NAME = "headerMap";

    private static class CIString implements Comparable<CIString>, Serializable {
        private static final long serialVersionUID = 550532045010691235L;

        private String key;
        private String lowerKey;

        public CIString(final String key) {
            this.key = key.trim();
            this.lowerKey = this.key.toLowerCase(StreamUtil.DEFAULT_LOCALE);
        }

        @Override
        public int hashCode() {
            return lowerKey.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof CIString)) {
                return false;
            }
            return key.equalsIgnoreCase(((CIString) obj).key);
        }

        @Override
        public int compareTo(final CIString o) {
            return lowerKey.compareTo(o.lowerKey);
        }

        @Override
        public String toString() {
            return key;
        }
    }

    private static class CIEntryAdaptor implements Entry<String, String> {
        private Entry<CIString, String> realEntry;

        private CIEntryAdaptor(final Entry<CIString, String> realEntry) {
            this.realEntry = realEntry;
        }

        @Override
        public String getKey() {
            return realEntry.getKey().key;
        }

        @Override
        public String getValue() {
            return realEntry.getValue();
        }

        @Override
        public String setValue(final String value) {
            return realEntry.setValue(value);
        }
    }

    private HashMap<CIString, String> realMap = new HashMap<CIString, String>();

    public static final String HEADER_DELIMITER = ":";

    public HeaderMap cloneAllowable() {
        final HeaderMap headerMap = new HeaderMap();
        headerMap.putAll(this);
        headerMap.removeAll(StroomHeaderArguments.HEADER_CLONE_EXCLUDE_SET);
        return headerMap;
    }

    public void read(final InputStream inputStream, final boolean close) throws IOException {
        final String data = StreamUtil.streamToString(inputStream, close);
        final String[] lines = data.split("\n");
        for (final String line : lines) {
            final int splitPos = line.indexOf(HEADER_DELIMITER);
            if (splitPos != -1) {
                final String key = line.substring(0, splitPos);
                final String value = line.substring(splitPos + 1);
                put(key, value);
            } else {
                put(line.trim(), null);
            }
        }
    }

    public void read(final byte[] data1) throws IOException {
        final String data = new String(data1, StreamUtil.DEFAULT_CHARSET);
        final String[] lines = data.split("\n");
        for (final String line : lines) {
            final int splitPos = line.indexOf(HEADER_DELIMITER);
            String key = null;
            String value = null;

            if (splitPos != -1) {
                key = line.substring(0, splitPos).trim();
                value = line.substring(splitPos + 1).trim();
            } else {
                key = line.trim();
            }

            if (key != null && key.length() > 0) {
                put(key, value);
            }
        }
    }

    public void write(final OutputStream outputStream, final boolean close) throws IOException {
        write(new OutputStreamWriter(outputStream, StreamUtil.DEFAULT_CHARSET), close);
    }

    public void write(final Writer writer, final boolean close) throws IOException {
        try {
            final List<CIString> sortedKeys = new ArrayList<CIString>(realMap.keySet());
            Collections.sort(sortedKeys);
            for (final CIString key : sortedKeys) {
                writer.write(key.key);
                final String value = realMap.get(key);
                if (value != null) {
                    writer.write(":");
                    writer.write(value);
                }
                writer.write("\n");
            }
            if (!close) {
                writer.flush();
            }
        } finally {
            if (close) {
                CloseableUtil.close(writer);
            }
        }
    }

    /**
     * <p>
     * Load Our Args into a hashmap.
     * </p>
     *
     * @param args
     *            command line argments
     */
    public void loadArgs(final String[] args) {
        for (int i = 0; i < args.length; i++) {
            final String[] split = args[i].split("=");
            if (split.length > 1) {
                put(split[0], split[1]);
            } else {
                put(split[0], "");
            }
        }
    }

    public byte[] toByteArray() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        write(byteArrayOutputStream, true);
        return byteArrayOutputStream.toByteArray();
    }

    public String getOrCreateGuid() {
        String guid = get(StroomHeaderArguments.GUID);
        if (guid == null) {
            guid = UUID.randomUUID().toString();
            put(StroomHeaderArguments.GUID, guid);
        }
        return guid;
    }

    public void removeAll(final Collection<String> keySet) {
        for (final String key : keySet) {
            remove(key);
        }
    }

    @Override
    public void clear() {
        realMap.clear();
    }

    @Override
    public boolean containsKey(final Object key) {
        return realMap.containsKey(new CIString((String) key));
    }

    @Override
    public boolean containsValue(final Object value) {
        return realMap.containsValue(value);
    }

    @Override
    public String get(final Object key) {
        return realMap.get(new CIString((String) key));
    }

    @Override
    public boolean isEmpty() {
        return realMap.isEmpty();
    }

    @Override
    public String put(final String key, String value) {
        if (value != null) {
            value = value.trim();
        }
        final CIString newKey = new CIString(key);
        final String oldValue = realMap.remove(newKey);
        realMap.put(newKey, value);
        return oldValue;
    }

    @Override
    public String remove(final Object key) {
        return realMap.remove(new CIString((String) key));
    }

    @Override
    public int size() {
        return realMap.size();
    }

    @Override
    public Set<java.util.Map.Entry<String, String>> entrySet() {
        final Set<java.util.Map.Entry<String, String>> rtnSet = new HashSet<>();
        for (final Entry<CIString, String> entry : realMap.entrySet()) {
            rtnSet.add(new CIEntryAdaptor(entry));
        }
        return rtnSet;
    }

    @Override
    public Set<String> keySet() {
        final Set<String> rtnSet = new HashSet<>();
        for (final CIString entry : realMap.keySet()) {
            rtnSet.add(entry.key);
        }
        return rtnSet;
    }

    @Override
    public void putAll(final Map<? extends String, ? extends String> m) {
        for (final Entry<? extends String, ? extends String> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Collection<String> values() {
        return realMap.values();
    }

    @Override
    public String toString() {
        return realMap.toString();
    }

}
