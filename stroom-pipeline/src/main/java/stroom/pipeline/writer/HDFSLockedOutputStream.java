/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.writer;

import stroom.util.io.WrappedOutputStream;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.io.OutputStream;

public class HDFSLockedOutputStream extends WrappedOutputStream {

    private static final String UNABLE_TO_RENAME_FILE = "Unable to rename file \"";
    private static final String TO = "\" to \"";
    private static final String QUOTE = "\"";

    private final Path lockFile;
    private final Path outFile;
    private final FileSystem hdfs;

    public HDFSLockedOutputStream(final OutputStream outputStream, final Path lockFile, final Path outFile,
                                  final FileSystem hdfs) {
        super(outputStream);
        this.lockFile = lockFile;
        this.outFile = outFile;
        this.hdfs = hdfs;
    }

    @Override
    public void close() throws IOException {
        super.flush();
        super.close();

        final boolean success = hdfs.rename(lockFile, outFile);

        if (!success) {
            final StringBuilder message = new StringBuilder();
            message.append(UNABLE_TO_RENAME_FILE);
            message.append(lockFile.toString());
            message.append(TO);
            message.append(outFile.toString());
            message.append(QUOTE);
            throw new IOException(message.toString());
        }
    }

    public Path getLockFile() {
        return lockFile;
    }

    public Path getOutFile() {
        return outFile;
    }
}
