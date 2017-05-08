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

import stroom.node.shared.Volume;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamstore.shared.StreamType.FileStoreType;
import stroom.streamstore.shared.StreamVolume;
import stroom.util.date.DateUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FileSystemStreamTypeUtil {
    /**
     * We use this rather than the File.separator as we need to be standard
     * across Windows and UNIX.
     */
    public static final char SEPERATOR_CHAR = '/';
    public static final char FILE_SEPERATOR_CHAR = '=';
    public static final String STORE_NAME = "store";

    private static String createFilePathBase(final Volume volume, final Stream stream, final StreamType streamType) {
        StringBuilder builder = new StringBuilder();
        builder.append(volume.getPath());
        builder.append(SEPERATOR_CHAR);
        builder.append(STORE_NAME);
        builder.append(SEPERATOR_CHAR);
        builder.append(getDirectory(stream, streamType));
        builder.append(SEPERATOR_CHAR);
        builder.append(getBaseName(stream));
        return builder.toString();
    }

    /**
     * Return back a input stream for a given stream type and file.
     */
    public static InputStream getInputStream(final StreamType streamType, final File file) throws IOException {
        if (streamType == null) {
            throw new IllegalArgumentException("Must Have a non-null stream type");
        }
        if (FileStoreType.bgz.equals(streamType.getFileStoreType())) {
            return new BlockGZIPInputFile(file);
        }
        return new UncompressedInputStream(file, streamType.isStreamTypeLazy());
    }

    /**
     * <p>
     * Find all existing child files of this parent.
     * </p>
     */
    public static List<File> findChildStreamFileList(final File parent) {
        List<File> kids = new ArrayList<File>();
        for (StreamType type : StreamType.initialValues()) {
            if (type.isStreamTypeChild()) {
                File child = createChildStreamFile(parent, type);
                if (child.isFile()) {
                    kids.add(child);
                }
            }
        }
        return kids;
    }

    /**
     * Return back a output stream for a given stream type and file.
     */
    public static OutputStream getOutputStream(final StreamType streamType, final Set<File> fileSet)
            throws IOException {
        if (streamType == null) {
            throw new IllegalArgumentException("Must Have a non-null stream type");
        }
        IOException ioEx = null;
        Set<OutputStream> outputStreamSet = new HashSet<OutputStream>();
        if (FileStoreType.bgz.equals(streamType.getFileStoreType())) {
            for (File file : fileSet) {
                try {
                    outputStreamSet.add(new BlockGZIPOutputFile(file));
                } catch (IOException e) {
                    ioEx = e;
                }
            }
        } else {
            for (File file : fileSet) {
                try {
                    outputStreamSet.add(new LockingFileOutputStream(file, streamType.isStreamTypeLazy()));
                } catch (IOException e) {
                    ioEx = e;
                }
            }
        }
        if (ioEx != null) {
            throw ioEx;
        }
        return ParallelOutputStream.createForStreamSet(outputStreamSet);
    }

    /**
     * Create a child file for a parent.
     */
    public static File createChildStreamFile(final StreamVolume streamVolume, final StreamType streamType) {
        StringBuilder builder = new StringBuilder();
        builder.append(createFilePathBase(streamVolume.getVolume(), streamVolume.getStream(),
                streamVolume.getStream().getStreamType()));
        builder.append(".");
        builder.append(streamVolume.getStream().getStreamType().getExtension());
        builder.append(".");
        builder.append(streamType.getExtension());
        builder.append(".");
        builder.append(String.valueOf(streamType.getFileStoreType()));
        return new File(builder.toString());
    }

    /**
     * <p>
     * Build a file base name.
     * </p>
     *
     * <p>
     * [feedid]_[streamid]
     * </p>
     */
    public static String getBaseName(Stream stream) {
        if (!stream.isPersistent()) {
            throw new RuntimeException("Can't build a file path until the meta data is persistent");
        }
        StringBuilder builder = new StringBuilder();
        builder.append(stream.getFeed().getId());
        builder.append(FILE_SEPERATOR_CHAR);
        builder.append(FileSystemPrefixUtil.padId(stream.getId()));
        return builder.toString();
    }

    public static String getDirectory(Stream stream, StreamType streamType) {
        StringBuilder builder = new StringBuilder();
        builder.append(streamType.getPath());
        builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
        String utcDate = DateUtil.createNormalDateTimeString(stream.getCreateMs());
        builder.append(utcDate.substring(0, 4));
        builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
        builder.append(utcDate.substring(5, 7));
        builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
        builder.append(utcDate.substring(8, 10));
        String idPath = FileSystemPrefixUtil.buildIdPath(FileSystemPrefixUtil.padId(stream.getId()));
        if (idPath != null) {
            builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
            builder.append(idPath);
        }
        return builder.toString();
    }

    /**
     * Create a child file set for a parent file set.
     */
    public static Set<File> createChildStreamFile(final Set<File> parentSet, final StreamType streamType) {
        Set<File> childSet = new HashSet<>();
        childSet.addAll(parentSet.stream().map(parent -> createChildStreamFile(parent, streamType))
                .collect(Collectors.toList()));
        return childSet;
    }

    /**
     * Find all the descendants to this file.
     */
    public static List<File> findAllDescendantStreamFileList(final File parent) {
        List<File> rtn = new ArrayList<>();
        List<File> kids = findChildStreamFileList(parent);
        for (File kid : kids) {
            rtn.add(kid);
            rtn.addAll(findAllDescendantStreamFileList(kid));
        }
        return rtn;
    }

    /**
     * Return a File IO object.
     */
    public static File createRootStreamFile(final Volume volume, final Stream stream, final StreamType streamType) {
        StringBuilder builder = new StringBuilder();
        builder.append(createFilePathBase(volume, stream, streamType));
        builder.append(".");
        builder.append(streamType.getExtension());
        builder.append(".");
        builder.append(String.valueOf(streamType.getFileStoreType()));

        return new File(builder.toString());
    }

    /**
     * Create a child file for a parent.
     */
    public static File createChildStreamFile(final File parent, final StreamType streamType) {
        StringBuilder builder = new StringBuilder(parent.getAbsolutePath());
        // Drop ".dat" or ".bgz"
        builder.setLength(builder.lastIndexOf("."));
        builder.append(".");
        builder.append(streamType.getExtension());
        builder.append(".");
        builder.append(String.valueOf(streamType.getFileStoreType()));
        return new File(builder.toString());
    }

}
