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

package stroom.pipeline.server.writer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import stroom.util.io.WrappedOutputStream;

public class LockedOutputStream extends WrappedOutputStream {
    private static final String UNABLE_TO_RENAME_FILE = "Unable to rename file \"";
    private static final String TO = "\" to \"";
    private static final String QUOTE = "\"";

    final File lockFile;
    final File outFile;

    public LockedOutputStream(final OutputStream outputStream, final File lockFile, final File outFile) {
        super(outputStream);
        this.lockFile = lockFile;
        this.outFile = outFile;
    }

    @Override
    public void close() throws IOException {
        super.flush();
        super.close();

        final boolean success = lockFile.renameTo(outFile);

        if (!success) {
            final StringBuilder message = new StringBuilder();
            message.append(UNABLE_TO_RENAME_FILE);
            message.append(lockFile.getAbsolutePath());
            message.append(TO);
            message.append(outFile.getAbsolutePath());
            message.append(QUOTE);
            throw new IOException(message.toString());
        }
    }
}
