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

package stroom.test;

import stroom.data.zip.StroomZipFileType;
import stroom.feed.api.FeedProperties;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.receive.common.ProgressHandler;
import stroom.receive.common.StreamTargetStreamHandlers;
import stroom.receive.common.StroomStreamProcessor;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

public class DataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataLoader.class);

    private static final String INPUT_EXTENSION = ".in";
    private static final String ZIP_EXTENSION = ".zip";

    private final FeedProperties feedProperties;
    private final StreamTargetStreamHandlers streamTargetStreamHandlers;

    public static final DateTimeFormatter EFFECTIVE_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH);

    public DataLoader(final FeedProperties feedProperties,
                      final StreamTargetStreamHandlers streamTargetStreamHandlers) {
        this.feedProperties = feedProperties;
        this.streamTargetStreamHandlers = streamTargetStreamHandlers;
    }

    public void read(final Path dir, final boolean mandateEffectiveDate, final Long effectiveMs) {
        readDir(dir, mandateEffectiveDate, effectiveMs);
    }

    private void readDir(final Path dir, final boolean mandateEffectiveDate, final Long effectiveMs) {
        try {
            Files.walkFileTree(dir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            try {
                                final String fileName = file.getFileName().toString().toLowerCase();
                                final long applicableEffectiveMs = getEffectiveTimeMsFromFileName(file)
                                        .orElse(effectiveMs);

                                if (fileName.endsWith(INPUT_EXTENSION)) {
                                    loadInputFile(file, mandateEffectiveDate, applicableEffectiveMs);

                                } else if (fileName.endsWith(ZIP_EXTENSION)) {
                                    loadZipFile(file, mandateEffectiveDate, applicableEffectiveMs);
                                }
                            } catch (final RuntimeException e) {
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
        final String feedName = getFeedName(file);
        try (final InputStream metaInputStream = createTestMetaInputStream();
                final InputStream dataInputStream = Files.newInputStream(file)) {
            loadInputStream(
                    feedName,
                    FileUtil.getCanonicalPath(file),
                    metaInputStream,
                    dataInputStream,
                    mandateEffectiveDate,
                    effectiveMs);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void loadInputStream(final String feedName,
                                final String info,
                                final InputStream metaInputStream,
                                final InputStream dataInputStream,
                                final boolean mandateEffectiveDate,
                                final Long effectiveMs) {
        final boolean isReference = feedProperties.isReference(feedName);
        if (isReference == mandateEffectiveDate) {
            final String effDateStr = effectiveMs != null
                    ? Instant.ofEpochMilli(effectiveMs).toString()
                    : "null";
            LOGGER.info("Loading data: " + info + " with eff. date " + effDateStr);

            final AttributeMap map = new AttributeMap();
            map.put(StandardHeaderArguments.FEED, feedName);
            map.putDateTime(StandardHeaderArguments.EFFECTIVE_TIME, effectiveMs);
            map.putDateTime(StandardHeaderArguments.RECEIVED_TIME, effectiveMs);
            map.putDateTime(StandardHeaderArguments.RECEIVED_TIME_HISTORY, effectiveMs);

            final ProgressHandler progressHandler = new ProgressHandler("Data Loader");
            streamTargetStreamHandlers.handle(feedName, null, map, handler -> {
                try {
                    // Write meta.
                    if (metaInputStream != null) {
                        handler.addEntry(
                                "001" + StroomZipFileType.META.getDotExtension(),
                                metaInputStream,
                                progressHandler);
                    }

                    // Write data.
                    if (dataInputStream != null) {
                        handler.addEntry(
                                "001" + StroomZipFileType.DATA.getDotExtension(),
                                dataInputStream,
                                progressHandler);
                    }

                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private InputStream createTestMetaInputStream() throws IOException {
        final AttributeMap meta = new AttributeMap();
        meta.put("TestData", "Loaded By SetupSampleData");
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AttributeMapUtil.write(meta, byteArrayOutputStream);
        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }

    private void loadZipFile(final Path file, final boolean mandateEffectiveDate, final Long effectiveMs) {
        final String feedName = getFeedName(file);

        if (feedProperties.isReference(feedName) == mandateEffectiveDate) {
            LOGGER.info("Loading data: " + FileUtil.getCanonicalPath(file));

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(StandardHeaderArguments.FEED, feedName);
            attributeMap.put("TestData", "Loaded By SetupSampleData");
            attributeMap.putDateTime(StandardHeaderArguments.EFFECTIVE_TIME, effectiveMs);

            final ProgressHandler progressHandler = new ProgressHandler("Data Loader");
            streamTargetStreamHandlers.handle(feedName, null, attributeMap, handler -> {
                // Use the Stroom stream processor to send zip entries in a consistent order.
                final StroomStreamProcessor stroomStreamProcessor = new StroomStreamProcessor(
                        attributeMap, handler, progressHandler);
                stroomStreamProcessor.processZipFile(file);
            });
        }
    }

    private String getFeedName(final Path file) {
        // Get the stem of the file name.
        String stem = getBaseName(file);

        final int index = stem.indexOf('~');
        if (index != -1) {
            stem = stem.substring(0, index);
        }

        return stem;
    }

    private String getBaseName(final Path file) {
        final String baseName = file.getFileName().toString();
        final int index = baseName.indexOf('.');
        if (index != -1) {
            return baseName.substring(0, index);
        } else {
            return baseName;
        }
    }

    private Optional<Long> getEffectiveTimeMsFromFileName(final Path file) {
        try {
            final String baseName = getBaseName(file);
            final String[] parts = baseName.split("~");
            if (parts.length == 3) {
                final String effectiveDateStr = parts[2];

                return Optional.of(DataLoader.EFFECTIVE_DATE_FORMATTER
                        .parse(effectiveDateStr, LocalDateTime::from)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli());
            } else {
                return Optional.empty();
            }
        } catch (final Exception e) {
            throw new RuntimeException("Unable to parse effective date from " + file.toString(), e);
        }
    }
}
