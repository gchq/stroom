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

import java.io.IOException;
import java.io.OutputStream;

public class OutputProxy implements Output {

    private final Output output;

    public OutputProxy(final Output output) {
        this.output = output;
    }

    @Override
    public OutputStream getOutputStream() {
        return output.getOutputStream();
    }

    @Override
    public void insertSegmentMarker() throws IOException {
        output.insertSegmentMarker();
    }

    @Override
    public void startZipEntry() throws IOException {
        output.startZipEntry();
    }

    @Override
    public void endZipEntry() throws IOException {
        output.endZipEntry();
    }

    @Override
    public boolean isZip() {
        return output.isZip();
    }

    @Override
    public long getCurrentOutputSize() {
        return output.getCurrentOutputSize();
    }

    @Override
    public boolean getHasBytesWritten() {
        return output.getHasBytesWritten();
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        output.write(bytes);
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
