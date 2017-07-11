/*
 * Copyright 2017 Crown Copyright
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

package stroom.proxy.repo;

import stroom.util.io.CloseableUtil;
import stroom.util.zip.StroomZipOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class StroomZipOutputStreamUtil {
    private StroomStreamHandlerUtil() {
        // Utlity class.
    }

    public static void addSimpleEntry(StroomZipOutputStream stroomZipOutputStream, StroomZipEntry entry, byte[] data)
            throws IOException {
        OutputStream outputStream = null;
        try {
            outputStream = stroomZipOutputStream.addEntry(entry);
            outputStream.write(data);
        } finally {
            CloseableUtil.close(outputStream);
        }
    }

    public static StroomStreamHandler createStroomStreamHandler(final StroomZipOutputStream stroomZipOutputStream) {
        return new StroomStreamHandler() {
            private OutputStream outputStream;

            @Override
            public void handleEntryStart(StroomZipEntry stroomZipEntry) throws IOException {
                outputStream = stroomZipOutputStream.addEntry(stroomZipEntry);
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
