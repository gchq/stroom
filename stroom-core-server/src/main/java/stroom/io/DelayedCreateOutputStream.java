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

package stroom.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A lazy output stream that only gets created when it is needed. It has a call
 * back that you implement to get hold of the real stream.
 * <p>
 * If you never write anything to it (i.e. createOutputStream is not called) it
 * does not bother doing anything on close or flush
 */
public abstract class DelayedCreateOutputStream extends OutputStream {
    private OutputStream outputStream;

    protected abstract OutputStream createOutputStream();

    private OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = createOutputStream();
        }
        return outputStream;
    }

    @Override
    public void write(int b) throws IOException {
        getOutputStream().write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        getOutputStream().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        getOutputStream().write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (outputStream != null) {
            outputStream.close();
        }
    }
}
