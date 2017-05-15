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

package stroom.util.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FilterOutputStreamProgressMonitor;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.io.StreamUtil;
import stroom.util.io.WrappedOutputStream;
import stroom.util.shared.Monitor;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class StroomZipOutputStream implements Closeable {
    public static final String LOCK_EXTENSION = ".lock";
    private static Logger LOGGER = LoggerFactory.getLogger(StroomZipOutputStream.class);
    private final File resultantFile;
    private final File lockFile;
    private final Monitor monitor;
    private final ZipOutputStream zipOutputStream;
    private final StreamProgressMonitor streamProgressMonitor;
    private StroomZipNameSet stroomZipNameSet;
    private boolean inEntry = false;
    private long entryCount = 0;

    public StroomZipOutputStream(final File file) throws IOException {
        this(file, null);
    }

    public StroomZipOutputStream(final File file, final Monitor monitor) throws IOException {
        this(file, monitor, true);
    }

    public StroomZipOutputStream(final File file, final Monitor monitor, final boolean monitorEntries) throws IOException {
        this.monitor = monitor;

        resultantFile = new File(file.getAbsolutePath());
        lockFile = new File(resultantFile.getAbsolutePath() + LOCK_EXTENSION);
        if (resultantFile.delete()) {
            LOGGER.warn("deleted file " + resultantFile);
        }
        if (lockFile.delete()) {
            LOGGER.warn("deleted file " + lockFile);
        }
        streamProgressMonitor = new StreamProgressMonitor(monitor, "Write");
        zipOutputStream = new ZipOutputStream(
                new FilterOutputStreamProgressMonitor(new FileOutputStream(lockFile), streamProgressMonitor));
        if (monitorEntries) {
            stroomZipNameSet = new StroomZipNameSet(false);
        }
    }

    public StroomZipOutputStream(final OutputStream outputStream) throws IOException {
        this(outputStream, null);
    }

    public StroomZipOutputStream(final OutputStream outputStream, final Monitor monitor) throws IOException {
        this.monitor = monitor;

        resultantFile = null;
        lockFile = null;
        streamProgressMonitor = new StreamProgressMonitor(monitor, "Write");
        zipOutputStream = new ZipOutputStream(
                new FilterOutputStreamProgressMonitor(new BufferedOutputStream(outputStream), streamProgressMonitor));
        stroomZipNameSet = new StroomZipNameSet(false);
    }

    public long getProgressSize() {
        if (streamProgressMonitor != null) {
            return streamProgressMonitor.getTotalBytes();
        }
        return -1;
    }

    public OutputStream addEntry(final StroomZipEntry entry) throws IOException {
        if (inEntry) {
            throw new RuntimeException("Failed to close last entry");
        }
        entryCount++;
        inEntry = true;
        if (monitor != null) {
            if (monitor.isTerminated()) {
                throw new IOException("Progress Stopped");
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("addEntry() - " + resultantFile + " - " + entry + " - adding");
        }
        if (stroomZipNameSet != null) {
            stroomZipNameSet.add(entry.getFullName());
        }
        zipOutputStream.putNextEntry(new ZipEntry(entry.getFullName()));
        return new WrappedOutputStream(zipOutputStream) {
            @Override
            public void close() throws IOException {
                zipOutputStream.closeEntry();
                inEntry = false;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("addEntry() - " + resultantFile + " - " + entry + " - closed");
                }
            }
        };
    }

    public long getEntryCount() {
        return entryCount;
    }

    public void addMissingMetaMap(final HeaderMap headerMap) throws IOException {
        if (stroomZipNameSet == null) {
            throw new RuntimeException("You can only add missing meta data if you are monitoring entries");

        }
        for (final String baseName : stroomZipNameSet.getBaseNameList()) {
            if (stroomZipNameSet.getName(baseName, StroomZipFileType.Meta) == null) {
                zipOutputStream.putNextEntry(new ZipEntry(baseName + StroomZipFileType.Meta.getExtension()));
                headerMap.write(zipOutputStream, false);
                zipOutputStream.closeEntry();
            }
        }
    }

    @Override
    public void close() throws IOException {
        // ZIP's don't like to be empty !
        if (entryCount == 0) {
            closeDelete();
        } else {
            zipOutputStream.close();
            if (lockFile != null) {
                if (!lockFile.renameTo(resultantFile)) {
                    throw new IOException("Failed to rename file " + lockFile + " to " + resultantFile);
                }
            }
        }
    }

    public void closeDelete() throws IOException {
        // ZIP's don't like to be empty !
        if (entryCount == 0) {
            final OutputStream os = addEntry(new StroomZipEntry("NULL.DAT", "NULL", StroomZipFileType.Data));
            os.write("NULL".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.close();
        }

        zipOutputStream.close();
        if (lockFile != null) {
            if (!lockFile.delete()) {
                throw new IOException("Failed to delete file " + lockFile);
            }
        }
    }

    public File getFinalFile() {
        return resultantFile;
    }
}
