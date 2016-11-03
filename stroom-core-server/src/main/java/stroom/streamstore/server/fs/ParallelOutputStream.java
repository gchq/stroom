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

package stroom.streamstore.server.fs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import stroom.io.SeekableOutputStream;

/**
 * Class to write the same data to a number of streams.
 */
public class ParallelOutputStream extends OutputStream implements SeekableOutputStream {
    private List<OutputStream> outputStreamList;
    private int outputStreamListSize = 0;

    private static Set<OutputStream> createOutputStreamsForFiles(final Set<File> outFileSet)
            throws FileNotFoundException {
        Set<OutputStream> rtn = new HashSet<>();
        for (File file : outFileSet) {
            rtn.add(new FileOutputStream(file));
        }
        return rtn;
    }

    /**
     * Write to this set.
     */
    public ParallelOutputStream(final Set<OutputStream> outputStreamSet) {
        this.outputStreamList = new ArrayList<>(outputStreamSet);
        this.outputStreamListSize = outputStreamList.size();
    }

    public static ParallelOutputStream create(final Set<File> outFileSet) throws FileNotFoundException {
        return new ParallelOutputStream(createOutputStreamsForFiles(outFileSet));
    }

    public static OutputStream createForStreamSet(final Set<OutputStream> outputStreamSet)
            throws FileNotFoundException {
        if (outputStreamSet.size() == 1) {
            return outputStreamSet.iterator().next();
        }
        return new ParallelOutputStream(outputStreamSet);
    }

    @Override
    public void write(final int b) throws IOException {
        for (int i = 0; i < outputStreamListSize; i++) {
            outputStreamList.get(i).write(b);
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        for (int i = 0; i < outputStreamListSize; i++) {
            outputStreamList.get(i).write(b);
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        for (int i = 0; i < outputStreamListSize; i++) {
            outputStreamList.get(i).write(b, off, len);
        }
    }

    @Override
    public void close() throws IOException {
        IOException ioEx = null;
        for (int i = 0; i < outputStreamListSize; i++) {
            try {
                outputStreamList.get(i).close();
            } catch (IOException e) {
                ioEx = e;
            }
        }
        if (ioEx != null) {
            throw ioEx;
        }
    }

    @Override
    public void flush() throws IOException {
        for (int i = 0; i < outputStreamListSize; i++) {
            outputStreamList.get(i).flush();
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ParallelOutputStream");

        for (int i = 0; i < outputStreamListSize; i++) {
            builder.append("\nParallel Stream=");
            builder.append(outputStreamList.get(i).toString());
        }
        return builder.toString();
    }

    @Override
    public long getPosition() throws IOException {
        return ((SeekableOutputStream) outputStreamList.get(0)).getPosition();
    }

    @Override
    public long getSize() throws IOException {
        return ((SeekableOutputStream) outputStreamList.get(0)).getSize();
    }

    @Override
    public void seek(long pos) throws IOException {
        for (int i = 0; i < outputStreamListSize; i++) {
            ((SeekableOutputStream) outputStreamList.get(i)).seek(pos);
        }
    }

}
