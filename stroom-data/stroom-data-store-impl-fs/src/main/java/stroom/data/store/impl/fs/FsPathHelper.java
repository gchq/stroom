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

package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.util.date.DateUtil;
import stroom.util.io.FileUtil;

import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

class FsPathHelper {
    /**
     * We use this rather than the File.separator as we need to be standard
     * across Windows and UNIX.
     */
//    private static final String SEPERATOR_CHAR = "/";
    private static final String FILE_SEPERATOR_CHAR = "=";
    private static final String STORE_NAME = "store";

    private static final String[] CHILD_STREAM_TYPES = new String[]{
            InternalStreamTypeNames.SEGMENT_INDEX,
            InternalStreamTypeNames.BOUNDARY_INDEX,
            InternalStreamTypeNames.MANIFEST,
            StreamTypeNames.META,
            StreamTypeNames.CONTEXT};

    private final FsFeedPathDao fileSystemFeedPaths;
    private final FsTypePathDao fileSystemTypePaths;

    @Inject
    FsPathHelper(final FsFeedPathDao fileSystemFeedPaths,
                 final FsTypePathDao fileSystemTypePaths) {
        this.fileSystemFeedPaths = fileSystemFeedPaths;
        this.fileSystemTypePaths = fileSystemTypePaths;
    }

//    private String createFilePathBase(final String rootPath, final Meta meta, final String streamTypeName) {
//        return rootPath +
//                SEPERATOR_CHAR +
//                STORE_NAME +
//                SEPERATOR_CHAR +
//                getDirectory(meta, streamTypeName) +
//                SEPERATOR_CHAR +
//                getBaseName(meta);
//    }
//
//    private Path createFilePathBase(final Path rootPath, final Meta meta, final String streamTypeName) {
//        Path result = rootPath.resolve(STORE_NAME);
//        result = appendDirectory(result, meta, streamTypeName);
//        return result.resolve(getBaseName(meta));
//    }

    /**
     * Return back a input stream for a given stream type and file.
     */
    public InputStream getInputStream(final String streamTypeName, final Path file) throws IOException {
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
    private List<Path> findChildStreamFileList(final Path parent) {
        final List<Path> kids = new ArrayList<>();
        for (final String type : CHILD_STREAM_TYPES) {
            final Path child = getChildPath(parent, type);
            if (Files.isRegularFile(child)) {
                kids.add(child);
            }
        }
        return kids;
    }

    /**
     * Return back a output stream for a given stream type and file.
     */
    public OutputStream getOutputStream(final String streamTypeName, final Path file)
            throws IOException {
        if (streamTypeName == null) {
            throw new IllegalArgumentException("Must Have a non-null stream type");
        }
        IOException ioEx = null;
        OutputStream outputStream = null;
        if (FileStoreType.bgz.equals(getFileStoreType(streamTypeName))) {
            try {
                outputStream = new BlockGZIPOutputFile(file);
            } catch (IOException e) {
                ioEx = e;
            }
        } else {
            try {
                outputStream = new LockingFileOutputStream(file, isStreamTypeLazy(streamTypeName));
            } catch (IOException e) {
                ioEx = e;
            }
        }
        if (ioEx != null) {
            throw ioEx;
        }
        return outputStream;
    }

//    /**
//     * Create a child file for a parent.
//     */
//    Path getChildPath(final Meta meta, final DataVolume streamVolume, final String streamTypeName) {
//        final String path = createFilePathBase(streamVolume.getVolumePath(), meta,
//                meta.getTypeName()) +
//                "." +
//                StreamTypeExtensions.getExtension(meta.getTypeName()) +
//                "." +
//                StreamTypeExtensions.getExtension(streamTypeName) +
//                "." +
//                String.valueOf(getFileStoreType(streamTypeName));
//        return Paths.get(path);
//    }

    /**
     * <p>
     * Build a file base name.
     * </p>
     * <p>
     * <p>
     * [feedid]_[streamid]
     * </p>
     */
    String getBaseName(Meta meta) {
        final String feedPath = fileSystemFeedPaths.getOrCreatePath(meta.getFeedName());
        return feedPath +
                FILE_SEPERATOR_CHAR +
                FsPrefixUtil.padId(meta.getId());
    }

//    String getDirectory(Meta meta, String streamTypeName) {
//        StringBuilder builder = new StringBuilder();
//        builder.append(fileSystemTypePaths.getOrCreatePath(streamTypeName));
//        builder.append(FsPathHelper.SEPERATOR_CHAR);
//        String utcDate = DateUtil.createNormalDateTimeString(meta.getCreateMs());
//        builder.append(utcDate, 0, 4);
//        builder.append(FsPathHelper.SEPERATOR_CHAR);
//        builder.append(utcDate, 5, 7);
//        builder.append(FsPathHelper.SEPERATOR_CHAR);
//        builder.append(utcDate, 8, 10);
//        String idPath = FsPrefixUtil.buildIdPath(FsPrefixUtil.padId(meta.getId()));
//        if (idPath != null) {
//            builder.append(FsPathHelper.SEPERATOR_CHAR);
//            builder.append(idPath);
//        }
//        return builder.toString();
//    }
//
//    Path appendDirectory(final Path path, final Meta meta, final String streamTypeName) {
//        final String utcDate = DateUtil.createNormalDateTimeString(meta.getCreateMs());
//        final String typePath = fileSystemTypePaths.getOrCreatePath(streamTypeName);
//        final String paddedId = FsPrefixUtil.padId(meta.getId());
//
//        return appendDirectory(path, typePath, utcDate, paddedId);
//    }
//
//    Path appendDirectory(final Path path, final String typePath, final String utcDate, final String paddedId) {
//        Path result = path;
//
//        result = result
//                .resolve(typePath)
//                .resolve(utcDate.substring(0, 4))
//                .resolve(utcDate.substring(5, 7))
//                .resolve(utcDate.substring(8, 10));
//
//        result = FsPrefixUtil.appendIdPath(result, paddedId);
//
//        return result;
//    }
//
//    /**
//     * Create a child file set for a parent file set.
//     */
//    Set<Path> getChildPathSet(final Set<Path> parentSet, final String streamTypeName) {
//        return parentSet.stream()
//                .map(parent -> getChildPath(parent, streamTypeName))
//                .collect(Collectors.toSet());
//    }

    /**
     * Find all the descendants to this file.
     */
    List<Path> findAllDescendantStreamFileList(final Path parent) {
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
    Path getRootPath(final Path volumePath, final Meta meta, final String streamTypeName) {
        final String utcDate = DateUtil.createNormalDateTimeString(meta.getCreateMs());
        final String typePath = fileSystemTypePaths.getOrCreatePath(streamTypeName);
        final String feedPath = fileSystemFeedPaths.getOrCreatePath(meta.getFeedName());
        final String paddedId = FsPrefixUtil.padId(meta.getId());

        final String fileName = "" +
                feedPath +
                FILE_SEPERATOR_CHAR +
                paddedId +
                "." +
                StreamTypeExtensions.getExtension(streamTypeName) +
                "." +
                getFileStoreType(streamTypeName);

        Path result = volumePath;
        result = result
                .resolve(STORE_NAME)
                .resolve(typePath)
                .resolve(utcDate.substring(0, 4))
                .resolve(utcDate.substring(5, 7))
                .resolve(utcDate.substring(8, 10));

        result = FsPrefixUtil.appendIdPath(result, paddedId);

        return result.resolve(fileName);
    }

    /**
     * Create a child file for a parent.
     */
    Path getChildPath(final Path parent, final String streamTypeName) {
        StringBuilder builder = new StringBuilder(FileUtil.getCanonicalPath(parent));
        // Drop ".dat" or ".bgz"
        builder.setLength(builder.lastIndexOf("."));
        builder.append(".");
        builder.append(StreamTypeExtensions.getExtension(streamTypeName));
        builder.append(".");
        builder.append(getFileStoreType(streamTypeName));
        return Paths.get(builder.toString());
    }

    String decodeStreamType(final Path path) {
        Objects.requireNonNull(path);

        final Pattern internalTypesPattern = Pattern.compile("\\.(" +
                StreamTypeExtensions.getExtension(InternalStreamTypeNames.BOUNDARY_INDEX) + "|" +
                StreamTypeExtensions.getExtension(InternalStreamTypeNames.SEGMENT_INDEX) + "|" +
                StreamTypeExtensions.getExtension(InternalStreamTypeNames.MANIFEST) + ")");

        // remove the internal types e.g. .revt.bdy. => .revt.
        final String pathStr = internalTypesPattern.matcher(path.toString())
                .replaceFirst("");

        final String[] splits = pathStr.split("\\.");

        if (splits.length >= 3) {
            final String typeExt = splits[splits.length - 2];
            return StreamTypeExtensions.getType(typeExt);
        } else {
            throw new RuntimeException("Unable to extract type from " + pathStr);
        }
    }

    /**
     * Gets all files associated with a parent.
     */
    List<Path> getFiles(final Path parent) throws IOException {
        String glob = parent.getFileName().toString();
        int index = glob.lastIndexOf(".");
        if (index != -1) {
            glob = glob.substring(0, index);
        }
        glob = "glob:**" + File.separator + glob + ".*";

        final List<Path> result = new ArrayList<>();
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob);
        Files.walkFileTree(parent.getParent(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                if (matcher.matches(file)) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    private FileStoreType getFileStoreType(final String streamTypeName) {
        if (InternalStreamTypeNames.SEGMENT_INDEX.equals(streamTypeName)) {
            return FileStoreType.dat;
        }
        if (InternalStreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName)) {
            return FileStoreType.dat;
        }
        if (InternalStreamTypeNames.MANIFEST.equals(streamTypeName)) {
            return FileStoreType.dat;
        }
        return FileStoreType.bgz;
    }

    boolean isStreamTypeLazy(final String streamTypeName) {
        return InternalStreamTypeNames.SEGMENT_INDEX.equals(streamTypeName) || InternalStreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName);
    }
//
//    static boolean isStreamTypeSegment(final String streamTypeName) {
//        return InternalStreamTypeNames.SEGMENT_INDEX.equals(streamTypeName) || InternalStreamTypeNames.BOUNDARY_INDEX.equals(streamTypeName);
//    }

    /**
     * Types of file we are dealing with.
     */
    private enum FileStoreType {
        dat, // The cached uncompressed file.
        bgz // Block GZIP Compressed File.
    }
}
