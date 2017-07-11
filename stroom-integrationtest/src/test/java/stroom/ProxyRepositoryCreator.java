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

package stroom;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseResultList;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.feed.shared.FindFeedCriteria;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.zip.HeaderMap;
import stroom.util.zip.StroomHeaderArguments;
import stroom.util.zip.StroomZipEntry;
import stroom.util.zip.StroomZipFile;
import stroom.util.zip.StroomZipFileType;
import stroom.util.zip.StroomZipOutputStream;
import stroom.util.zip.StroomZipRepository;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProxyRepositoryCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyRepositoryCreator.class);

    private static final String INPUT_EXTENSION = ".in";
    private static final String ZIP_EXTENSION = ".zip";

    private final FeedService feedService;
    private final StroomZipRepository repository;

    public ProxyRepositoryCreator(final FeedService feedService, final StroomZipRepository repository) {
        this.feedService = feedService;
        this.repository = repository;
    }

    public void read(final File dir, final boolean mandateEffectiveDate, final Long effectiveMs) {
        readDir(dir, mandateEffectiveDate, effectiveMs);
    }

    private void readDir(final File dir, final boolean mandateEffectiveDate, final Long effectiveMs) {
        final File[] files = dir.listFiles();
        for (final File file : files) {
            if (!file.getName().startsWith(".")) {
                if (file.isDirectory()) {
                    // Recurse.
                    readDir(file, mandateEffectiveDate, effectiveMs);

                } else {
                    final String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(INPUT_EXTENSION)) {
                        loadInput(file, mandateEffectiveDate, effectiveMs);

                    } else if (fileName.endsWith(ZIP_EXTENSION)) {
                        loadZip(file, mandateEffectiveDate, effectiveMs);
                    }
                }
            }
        }
    }

    private void loadInput(final File file, final boolean mandateEffectiveDate, final Long effectiveMs) {
        // Get the feed.
        final Feed feed = getFeed(file);

        try {
            if (feed.isReference() == mandateEffectiveDate) {
                LOGGER.info("Loading data: " + file.getAbsolutePath());

                final StroomZipOutputStream zipOutputStream = repository.getStroomZipOutputStream();

                int i = 0;
                i++;
                String newName = Integer.toString(i);
                newName = StringUtils.leftPad(newName, 3, '0');

                // Add meta data.
                OutputStream zipPart = zipOutputStream.addEntry(new StroomZipEntry(null, newName, StroomZipFileType.Meta));
                final HeaderMap map = createMap(feed, effectiveMs);
                map.write(zipPart, true);

                // Add data.
                zipPart = zipOutputStream.addEntry(new StroomZipEntry(null, newName, StroomZipFileType.Data));
                StreamUtil.streamToStream(new BufferedInputStream(new FileInputStream(file)), zipPart);

                zipOutputStream.close();
            }
        } catch (final IOException e) {
            throw new RuntimeException("Error loading file: " + file.getAbsolutePath(), e);
        }
    }

    private void loadZip(final File file, final boolean mandateEffectiveDate, final Long effectiveMs) {
        // Get the feed.
        final Feed feed = getFeed(file);

        if (feed.isReference() == mandateEffectiveDate) {
            LOGGER.info("Loading data: " + file.getAbsolutePath());

            try {
                final StroomZipOutputStream zipOutputStream = repository.getStroomZipOutputStream();

                final StroomZipFile stroomZipFile = new StroomZipFile(file);

                int i = 0;
                for (String baseName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
                    i++;
                    String newName = Integer.toString(i);
                    newName = StringUtils.leftPad(newName, 3, '0');

                    // Add meta data.
                    InputStream inputStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Meta);
                    final HeaderMap map = createMap(feed, effectiveMs);
                    if (inputStream != null) {
                        map.read(inputStream, true);
                    }
                    OutputStream outputStream = zipOutputStream
                            .addEntry(new StroomZipEntry(null, newName, StroomZipFileType.Meta));
                    map.write(outputStream, true);

                    // Add context data.
                    inputStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Context);
                    if (inputStream != null) {
                        outputStream = zipOutputStream.addEntry(new StroomZipEntry(null, newName, StroomZipFileType.Context));
                        StreamUtil.streamToStream(inputStream, outputStream);
                    }

                    // Add data.
                    inputStream = stroomZipFile.getInputStream(baseName, StroomZipFileType.Data);
                    if (inputStream != null) {
                        outputStream = zipOutputStream.addEntry(new StroomZipEntry(null, newName, StroomZipFileType.Data));
                        StreamUtil.streamToStream(inputStream, outputStream);
                    }
                }

                stroomZipFile.close();
                zipOutputStream.close();

            } catch (final IOException e) {
                throw new RuntimeException("Error loading file: " + file.getAbsolutePath(), e);
            }
        }
    }

    private HeaderMap createMap(final Feed feed, final Long effectiveMs) {
        final String dateTime = DateUtil.createNormalDateTimeString(effectiveMs);

        final HeaderMap map = new HeaderMap();
        map.put(StroomHeaderArguments.FEED, feed.getName());
        map.put(StroomHeaderArguments.RECEIVED_TIME, dateTime);
        map.put(StroomHeaderArguments.EFFECTIVE_TIME, dateTime);
        map.put("TestData", "Loaded By SetupSampleData");

        return map;
    }

    private Feed getFeed(final File file) {
        // Get the stem of the file name.
        String stem = file.getName();
        int index = stem.indexOf('.');
        if (index != -1) {
            stem = stem.substring(0, index);
        }

        // Find the associated feed.
        final FindFeedCriteria findFeedCriteria = new FindFeedCriteria();
        findFeedCriteria.getName().setString(stem);
        final BaseResultList<Feed> list = feedService.find(findFeedCriteria);

        if (list.size() == 0) {
            throw new RuntimeException("Feed not found \"" + stem + "\"");
        }

        return list.getFirst();
    }
}
