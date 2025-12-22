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

package stroom.data.zip;

import stroom.util.io.WrappedOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class FilterOutputStreamProgressMonitor extends WrappedOutputStream {

    private final Consumer<Long> progressHandler;

    public FilterOutputStreamProgressMonitor(final OutputStream outputStream, final Consumer<Long> progressHandler) {
        super(outputStream);
        this.progressHandler = progressHandler;
    }

    @Override
    public void write(final byte[] b) throws IOException {
        super.write(b);
        progressHandler.accept((long) b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        super.write(b, off, len);
        progressHandler.accept((long) len);
    }

    @Override
    public void write(final int b) throws IOException {
        super.write(b);
        progressHandler.accept(1L);
    }
}
