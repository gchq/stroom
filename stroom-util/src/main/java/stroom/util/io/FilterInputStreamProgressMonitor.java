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

package stroom.util.io;

import stroom.util.task.TaskMonitor;

import java.io.IOException;
import java.io.InputStream;

public class FilterInputStreamProgressMonitor extends WrappedInputStream {
    private final StreamProgressMonitor streamProgressMonitor;

    public FilterInputStreamProgressMonitor(InputStream inputStream, TaskMonitor taskMonitor) {
        super(inputStream);
        this.streamProgressMonitor = new StreamProgressMonitor(taskMonitor, "Read ");
    }

    @Override
    public int read() throws IOException {
        int rtn = super.read();
        if (rtn != -1) {
            streamProgressMonitor.progress(1);
        }
        return rtn;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int rtn = super.read(b);
        if (rtn != -1) {
            streamProgressMonitor.progress(rtn);
        }
        return rtn;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int rtn = super.read(b, off, len);
        if (rtn != -1) {
            streamProgressMonitor.progress(rtn);
        }
        return rtn;
    }
}
