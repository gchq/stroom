/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.feed.FeedService;
import stroom.feed.MetaMap;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.proxy.repo.StroomZipEntry;
import stroom.proxy.repo.StroomZipFile;
import stroom.proxy.repo.StroomZipFileType;
import stroom.streamstore.StreamStore;
import stroom.streamstore.StreamTarget;
import stroom.streamstore.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.StreamTargetStroomStreamHandler;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class DataLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataLoader.class);

    private static final String INPUT_EXTENSION = ".in";
    private static final String ZIP_EXTENSION = ".zip";

    private final FeedService feedService;
    private final StreamStore streamStore;

    public DataLoader(final FeedService feedService, final StreamStore streamStore) {
        this.feedService = feedService;
        this.streamStore = streamStore;
    }

    public void read(final Path dir, final boolean mandateEffectiveDate, final Long effectiveMs) {
        readDir(dir, mandateEffectiveDate, effectiveMs);
    }

    private void readDir(final Path dir, final boolean mandateEffectiveDate, final Long effectiveMs) {
        try {
            Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    try {
                        final String fileName = file.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(INPUT_EXTENSION)) {
                            loadInputFile(file, mandateEffectiveDate, effectiveMs);

                        } else if (fileName.endsWith(ZIP_EXTENSION)) {
                            loadZipFile(file, mandateEffectiveDate, effectiveMs);
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    return super.visitFile(file, attrs);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void loadInputFile(final Path file, final boolean mandateEffectiveDate, final Long effectiveMs) {
        final Feed feed = getFeed(file);
        try (final InputStream inputStream = Files.newInputStream(file)) {
            loadInputStream(feed, FileUtil.getCanonicalPath(file), inputStream, mandateEffectiveDate, effectiveMs);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void loadInputStream(final Feed feed, final String info, final InputStream inputStream,
                                final boolean mandateEffectiveDate, final Long effectiveMs) {
        if (feed.isReference() == mandateEffectiveDate) {
            LOGGER.info("Loading data: " + info);

            StreamType streamType = StreamType.RAW_EVENTS;
            if (feed.isReference()) {
                streamType = StreamType.RAW_REFERENCE;
            }

            final Stream stream = Stream.createStream(streamType, feed, effectiveMs);
            final StreamTarget streamTarget = streamStore.openStreamTarget(stream);

            try {
                final RASegmentOutputStream outputStream = new RASegmentOutputStream(streamTarget);
                final RawInputSegmentWriter writer = new RawInputSegmentWriter();
                writer.write(new BufferedInputStream(inputStream), outputStream);

                final MetaMap map = new MetaMap();
                map.put("TestData", "Loaded By SetupSampleData");

                map.write(streamTarget.addChildStream(StreamType.META).getOutputStream(), true);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

            streamStore.closeStreamTarget(streamTarget);
        }
    }

    private void loadZipFile(final Path file, final boolean mandateEffectiveDate, final Long effectiveMs) {
        final Feed feed = getFeed(file);

        if (feed.isReference() == mandateEffectiveDate) {
            LOGGER.info("Loading data: " + FileUtil.getCanonicalPath(file));

            try {
                final StroomZipFile stroomZipFile = new StroomZipFile(file);
                final byte[] buffer = new byte[1024];
                final StreamTargetStroomStreamHandler streamTargetStroomStreamHandler = new StreamTargetStroomStreamHandler(
                        streamStore, feedService, null, feed, feed.getStreamType());

                final MetaMap map = new MetaMap();
                map.put("TestData", "Loaded By SetupSampleData");

                streamTargetStroomStreamHandler.handleHeader(map);
                for (final String baseName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
                    streamTargetStroomStreamHandler
                            .handleEntryStart(new StroomZipEntry(null, baseName, StroomZipFileType.Context));
                    InputStream inputStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Context);
                    int read = 0;
                    while ((read = inputStream.read(buffer)) != -1) {
                        streamTargetStroomStreamHandler.handleEntryData(buffer, 0, read);
                    }
                    streamTargetStroomStreamHandler.handleEntryEnd();

                    streamTargetStroomStreamHandler.handleEntryStart(new StroomZipEntry(null, baseName, StroomZipFileType.Data));
                    inputStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Data);
                    read = 0;
                    while ((read = inputStream.read(buffer)) != -1) {
                        streamTargetStroomStreamHandler.handleEntryData(buffer, 0, read);
                    }
                    streamTargetStroomStreamHandler.handleEntryEnd();

                }
                streamTargetStroomStreamHandler.close();

                stroomZipFile.close();

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private Feed getFeed(final Path file) {
        // Get the stem of the file name.
        String stem = file.getFileName().toString();
        int index = stem.indexOf('.');
        if (index != -1) {
            stem = stem.substring(0, index);
        }
        index = stem.indexOf('~');
        if (index != -1) {
            stem = stem.substring(0, index);
        }

        return getFeed(stem);
    }

    public Feed getFeed(final String name) {
        // Find the associated feed.
        final FindFeedCriteria findFeedCriteria = new FindFeedCriteria();
        findFeedCriteria.getName().setString(name);
        final BaseResultList<Feed> list = feedService.find(findFeedCriteria);

        if (list.size() == 0) {
            throw new RuntimeException("Feed not found \"" + name + "\"");
        }

        return list.getFirst();
    }
}
