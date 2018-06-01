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

package stroom.streamstore.fs;

import stroom.node.shared.Volume;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamTypeEntity.FileStoreType;
import stroom.streamstore.shared.StreamVolume;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final String SEPERATOR_CHAR = "/";
    private static final String FILE_SEPERATOR_CHAR = "=";
    private static final String STORE_NAME = "store";

    private static String[] CHILD_STREAM_TYPES = new String[]{
            StreamTypeNames.SEGMENT_INDEX,
            StreamTypeNames.BOUNDARY_INDEX,
            StreamTypeNames.MANIFEST,
            StreamTypeNames.META,
            StreamTypeNames.CONTEXT};

    private static String createFilePathBase(final Volume volume, final StreamEntity stream, final String streamTypeName) {
        return volume.getPath() +
                SEPERATOR_CHAR +
                STORE_NAME +
                SEPERATOR_CHAR +
                getDirectory(stream, streamTypeName) +
                SEPERATOR_CHAR +
                getBaseName(stream);
    }

    /**
     * Return back a input stream for a given stream type and file.
     */
    public static InputStream getInputStream(final String streamTypeName, final Path file) throws IOException {
        if (streamTypeName == null) {
            throw new IllegalArgumentException("Must Have a non-null stream type");
        }
        if (FileStoreType.bgz.equals(getFileStoreType(streamTypeName))) {
            return new BlockGZIPInputFile(file);
        }
        return new UncompressedInputStream(file, isStreamTypeLazy(streamTypeName));
    }

    /**
     * <p>
     * Find all existing child files of this parent.
     * </p>
     */
    private static List<Path> findChildStreamFileList(final Path parent) {
        final List<Path> kids = new ArrayList<>();
        for (final String type : CHILD_STREAM_TYPES) {
            final Path child = createChildStreamFile(parent, type);
            if (Files.isRegularFile(child)) {
                kids.add(child);
            }
        }
        return kids;
    }

    /**
     * Return back a output stream for a given stream type and file.
     */
    public static OutputStream getOutputStream(final String streamTypeName, final Set<Path> fileSet)
            throws IOException {
        if (streamTypeName == null) {
            throw new IllegalArgumentException("Must Have a non-null stream type");
        }
        IOException ioEx = null;
        Set<OutputStream> outputStreamSet = new HashSet<>();
        if (FileStoreType.bgz.equals(getFileStoreType(streamTypeName))) {
            for (Path file : fileSet) {
                try {
                    outputStreamSet.add(new BlockGZIPOutputFile(file));
                } catch (IOException e) {
                    ioEx = e;
                }
            }
        } else {
            for (Path file : fileSet) {
                try {
                    outputStreamSet.add(new LockingFileOutputStream(file, isStreamTypeLazy(streamTypeName)));
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
    public static Path createChildStreamFile(final StreamVolume streamVolume, final String streamTypeName) {
        final String path = createFilePathBase(streamVolume.getVolume(), streamVolume.getStream(),
                streamVolume.getStream().getStreamTypeName()) +
                "." +
                StreamTypeExtensions.getExtension(streamVolume.getStream().getStreamTypeName()) +
                "." +
                StreamTypeExtensions.getExtension(streamTypeName) +
                "." +
                String.valueOf(getFileStoreType(streamTypeName));
        return Paths.get(path);
    }

    /**
     * <p>
     * Build a file base name.
     * </p>
     * <p>
     * <p>
     * [feedid]_[streamid]
     * </p>
     */
    public static String getBaseName(StreamEntity stream) {
        if (!stream.isPersistent()) {
            throw new RuntimeException("Can't build a file path until the meta data is persistent");
        }
        return stream.getFeed().getId() +
                FILE_SEPERATOR_CHAR +
                FileSystemPrefixUtil.padId(stream.getId());
    }

    public static String getDirectory(StreamEntity stream, String streamTypeName) {
        StringBuilder builder = new StringBuilder();
        builder.append(StreamTypePaths.getPath(streamTypeName));
        builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
        String utcDate = DateUtil.createNormalDateTimeString(stream.getCreateMs());
        builder.append(utcDate, 0, 4);
        builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
        builder.append(utcDate, 5, 7);
        builder.append(FileSystemStreamTypeUtil.SEPERATOR_CHAR);
        builder.append(utcDate, 8, 10);
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
    static Set<Path> createChildStreamPath(final Set<Path> parentSet, final String streamTypeName) {
        return parentSet.stream()
                .map(parent -> createChildStreamFile(parent, streamTypeName))
                .collect(Collectors.toSet());
    }

    /**
     * Find all the descendants to this file.
     */
    public static List<Path> findAllDescendantStreamFileList(final Path parent) {
        List<Path> rtn = new ArrayList<>();
        List<Path> kids = findChildStreamFileList(parent);
        for (Path kid : kids) {
            rtn.add(kid);
            rtn.addAll(findAllDescendantStreamFileList(kid));
        }
        return rtn;
    }

    /**
     * Return a File IO object.
     */
    public static Path createRootStreamFile(final Volume volume, final StreamEntity stream, final String streamTypeName) {
        final String path = createFilePathBase(volume, stream, streamTypeName) +
                "." +
                StreamTypeExtensions.getExtension(streamTypeName) +
                "." +
                String.valueOf(getFileStoreType(streamTypeName));
        return Paths.get(path);
    }

    /**
     * Create a child file for a parent.
     */
    static Path createChildStreamFile(final Path parent, final String streamTypeName) {
        StringBuilder builder = new StringBuilder(FileUtil.getCanonicalPath(parent));
        // Drop ".dat" or ".bgz"
        builder.setLength(builder.lastIndexOf("."));
        builder.append(".");
        builder.append(StreamTypeExtensions.getExtension(streamTypeName));
        builder.append(".");
        builder.append(String.valueOf(getFileStoreType(streamTypeName)));
        return Paths.get(builder.toString());
    }

    private static FileStoreType getFileStoreType(final String streamTypeName) {
        if (StreamTypeNames.SEGMENT_INDEX.equals(streamTypeName)) {
            return FileStoreType.dat;
        }
        if (StreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName)) {
            return FileStoreType.dat;
        }
        if (StreamTypeNames.MANIFEST.equals(streamTypeName)) {
            return FileStoreType.dat;
        }
        return FileStoreType.bgz;
    }

    static boolean isStreamTypeLazy(final String streamTypeName) {
        return StreamTypeNames.SEGMENT_INDEX.equals(streamTypeName) || StreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName);
    }

    public static boolean isStreamTypeSegment(final String streamTypeName) {
        return StreamTypeNames.SEGMENT_INDEX.equals(streamTypeName) || StreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName);
    }
}
