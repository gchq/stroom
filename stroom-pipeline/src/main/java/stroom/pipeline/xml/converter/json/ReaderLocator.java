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

package stroom.pipeline.xml.converter.json;

import stroom.util.io.WrappedReader;

import org.xml.sax.Locator;

import java.io.IOException;
import java.io.Reader;

public class ReaderLocator extends WrappedReader implements Locator {

    private int lineNo = 1;
    private int colNo = 1;

    public ReaderLocator(final Reader reader) {
        super(reader);
    }

    @Override
    public int read() throws IOException {
        final int c = super.read();
        if (c != -1) {
            processChar((char) c);
        }
        return c;
    }

    @Override
    public int read(final char[] cbuf, final int off, final int len) throws IOException {
        final int length = super.read(cbuf, off, len);
        for (int i = 0; i < length; i++) {
            processChar(cbuf[i]);
        }
        return length;
    }

    @Override
    public int read(final char[] cbuf) throws IOException {
        final int length = super.read(cbuf);
        for (int i = 0; i < length; i++) {
            processChar(cbuf[i]);
        }
        return length;
    }

    private void processChar(final char c) {
        if (c == '\n') {
            lineNo++;
            colNo = 1;
        } else {
            colNo++;
        }
    }

    @Override
    public int getColumnNumber() {
        return colNo;
    }

    @Override
    public int getLineNumber() {
        return lineNo;
    }

    @Override
    public String getPublicId() {
        return null;
    }

    @Override
    public String getSystemId() {
        return null;
    }

    @Override
    public String toString() {
        return "[" + lineNo + ":" + colNo + "]";
    }
}
