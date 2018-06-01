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

package stroom.streamtask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.FeedDocCache;
import stroom.feed.MetaMap;
import stroom.feed.MetaMapFactory;
import stroom.feed.StroomHeaderArguments;
import stroom.feed.shared.FeedDoc;
import stroom.proxy.repo.StroomHeaderStreamHandler;
import stroom.proxy.repo.StroomStreamHandler;
import stroom.proxy.repo.StroomZipEntry;
import stroom.proxy.repo.StroomZipFileType;
import stroom.proxy.repo.StroomZipNameSet;
import stroom.streamstore.StreamFactory;
import stroom.streamstore.api.StreamProperties;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.api.StreamTarget;
import stroom.streamstore.fs.StreamTypeNames;
import stroom.streamstore.fs.serializable.NestedStreamTarget;
import stroom.streamstore.shared.FeedEntity;
import stroom.streamstore.shared.StreamEntity;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.streamtask.statistic.MetaDataStatistic;
import stroom.util.io.CloseableUtil;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Type of {@link StroomStreamHandler} that store the entries in the stream store.
 * There are some special rules about how this works.
 * <p>
 * This is fine if all the meta data indicates they belong to the same feed
 * 001.meta, 002.meta, 001.dat, 002.dat
 * <p>
 * This is also fine if 001.meta indicates 001 belongs to feed X and 002.meta
 * indicates 001 belongs to feed Y 001.meta, 002.meta, 001.dat, 002.dat
 * <p>
 * However if the global header map indicates feed Z and the files are send in
 * the following order 001.dat, 002.dat, 001.meta, 002.meta this is invalid ....
 * I.E. as soon as we add non header stream for a feed if the header turns out
 * to be different we must throw an exception.
 */
public class StreamTargetStroomStreamHandler implements StroomStreamHandler, StroomHeaderStreamHandler, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTargetStroomStreamHandler.class);

    private final StreamStore streamStore;
    private final FeedDocCache feedDocCache;
    private final MetaDataStatistic metaDataStatistics;
    private final HashSet<StreamEntity> streamSet;
    private final StroomZipNameSet stroomZipNameSet;
    private final Map<String, FeedEntity> feedMap = new HashMap<>();
    private final Map<String, StreamTypeEntity> streamTypeMap = new HashMap<>();
    private final Map<String, NestedStreamTarget> feedNestedStreamTarget = new HashMap<>();
    private final Map<String, StreamTarget> feedStreamTarget = new HashMap<>();
    private final ByteArrayOutputStream currentHeaderByteArrayOutputStream = new ByteArrayOutputStream();
    private boolean oneByOne;
    private StroomZipFileType currentFileType = null;
    private StroomZipEntry currentStroomZipEntry = null;
    private StroomZipEntry lastDatStroomZipEntry = null;
    private StroomZipEntry lastCtxStroomZipEntry = null;
    private String currentFeedName;
    private String currentStreamTypeName;
    private MetaMap globalMetaMap;
    private MetaMap currentMetaMap;

    public StreamTargetStroomStreamHandler(final StreamStore streamStore,
                                           final FeedDocCache feedDocCache,
                                           final MetaDataStatistic metaDataStatistics,
                                           final String feedName,
                                           final String streamTypeName) {
        this.streamStore = streamStore;
        this.feedDocCache = feedDocCache;
        this.metaDataStatistics = metaDataStatistics;
        this.currentFeedName = feedName;
        this.currentStreamTypeName = streamTypeName;
        this.streamSet = new HashSet<>();
        this.stroomZipNameSet = new StroomZipNameSet(true);
    }

    public static List<StreamTargetStroomStreamHandler> buildSingleHandlerList(final StreamStore streamStore,
                                                                               final FeedDocCache feedDocCache,
                                                                               final MetaDataStatistic metaDataStatistics,
                                                                               final String feedName,
                                                                               final String streamTypeName) {
        final ArrayList<StreamTargetStroomStreamHandler> list = new ArrayList<>();
        list.add(new StreamTargetStroomStreamHandler(streamStore, feedDocCache, metaDataStatistics, feedName, streamTypeName));
        return list;
    }

    public void setOneByOne(final boolean oneByOne) {
        this.oneByOne = oneByOne;
    }

    @Override
    public void handleHeader(final MetaMap metaMap) {
        globalMetaMap = metaMap;
    }

    @Override
    public void handleEntryData(final byte[] data, final int off, final int len) throws IOException {
        if (currentFileType.equals(StroomZipFileType.Meta)) {
            currentHeaderByteArrayOutputStream.write(data, off, len);
        }
        if (StroomZipFileType.Data.equals(currentFileType)) {
            getCurrentNestedStreamTarget().getOutputStream().write(data, off, len);
        }
        if (StroomZipFileType.Context.equals(currentFileType)) {
            getCurrentNestedStreamTarget().getOutputStream(StreamTypeNames.CONTEXT).write(data, off, len);
        }
    }

//    private Fd getFeed(final String name) {
//        return feedMap.computeIfAbsent(name, k -> {
//            final Optional<FeedDoc> feedDoc = feedDocCache.get(name);
//            if (feedDoc == null) {
//                throw new RuntimeException("Unable to get feed " + k);
//            }
//            return fd;
//        });
//    }
//
//    private StreamType getStreamType(final String name) {
//        return streamTypeMap.computeIfAbsent(name, k -> {
//            final StreamType streamType = streamTypeService.loadByName(k);
//            if (streamType == null) {
//                throw new RuntimeException("Unable to get stream type " + k);
//            }
//            return streamType;
//        });
//    }

    @Override
    public void handleEntryStart(final StroomZipEntry stroomZipEntry) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleEntryStart() - " + stroomZipEntry);
        }

        currentFileType = stroomZipEntry.getStroomZipFileType();

        // We don't want to aggregate reference feeds.
        final boolean singleEntry = isReference(currentFeedName) || oneByOne;

        final StroomZipEntry nextEntry = stroomZipNameSet.add(stroomZipEntry.getFullName());

        if (singleEntry && currentStroomZipEntry != null && !nextEntry.equalsBaseName(currentStroomZipEntry)) {
            // Close it if we have opened it.
            if (feedStreamTarget.containsKey(currentFeedName)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("handleEntryStart() - Closing due to singleEntry=" + singleEntry + " " + currentFeedName
                            + " currentStroomZipEntry=" + currentStroomZipEntry + " nextEntry=" + nextEntry);
                }
                closeCurrentFeed();
            }
        }

        currentStroomZipEntry = nextEntry;

        if (StroomZipFileType.Meta.equals(currentFileType)) {
            // Header we just buffer up
            currentHeaderByteArrayOutputStream.reset();
        }
        if (StroomZipFileType.Data.equals(currentFileType)) {
            getCurrentNestedStreamTarget().putNextEntry();
        }
        if (StroomZipFileType.Context.equals(currentFileType)) {
            getCurrentNestedStreamTarget().putNextEntry(StreamTypeNames.CONTEXT);
        }

    }

    private String getStreamTypeName(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::getStreamType)
                .orElse(StreamTypeEntity.RAW_EVENTS.getName());
    }

    private boolean isReference(final String feedName) {
        return feedDocCache.get(feedName)
                .map(FeedDoc::isReference)
                .orElse(false);
    }

    @Override
    public void handleEntryEnd() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleEntryEnd() - " + currentFileType);
        }

        if (StroomZipFileType.Meta.equals(currentFileType)) {
            currentMetaMap = null;
            if (globalMetaMap != null) {
                currentMetaMap = MetaMapFactory.cloneAllowable(globalMetaMap);
            } else {
                currentMetaMap = new MetaMap();
            }
            currentMetaMap.read(currentHeaderByteArrayOutputStream.toByteArray());

            if (metaDataStatistics != null) {
                metaDataStatistics.recordStatistics(currentMetaMap);
            }

            // Are we switching feed?
            final String feedName = currentMetaMap.get(StroomHeaderArguments.FEED);
            if (feedName != null) {
                if (currentFeedName == null || !currentFeedName.equals(feedName)) {
                    // Yes ... load the new feed
                    currentFeedName = feedName;
                    currentStreamTypeName = getStreamTypeName(currentFeedName);

                    final String currentBaseName = currentStroomZipEntry.getBaseName();

                    // Have we stored some data or context
                    if (lastDatStroomZipEntry != null
                            && stroomZipNameSet.getBaseName(lastDatStroomZipEntry.getFullName()).equals(currentBaseName)) {
                        throw new IOException("Header and Data out of order for multiple feed data");
                    }
                    if (lastCtxStroomZipEntry != null
                            && stroomZipNameSet.getBaseName(lastCtxStroomZipEntry.getFullName()).equals(currentBaseName)) {
                        throw new IOException("Header and Data out of order for multiple feed data");
                    }
                }
            }

            getCurrentNestedStreamTarget().putNextEntry(StreamTypeNames.META);
            getCurrentNestedStreamTarget().getOutputStream(StreamTypeNames.META)
                    .write(currentHeaderByteArrayOutputStream.toByteArray());
            getCurrentNestedStreamTarget().closeEntry(StreamTypeNames.META);

        }
        if (StroomZipFileType.Data.equals(currentFileType)) {
            getCurrentNestedStreamTarget().closeEntry();
            lastDatStroomZipEntry = currentStroomZipEntry;
        }
        if (StroomZipFileType.Context.equals(currentFileType)) {
            getCurrentNestedStreamTarget().closeEntry(StreamTypeNames.CONTEXT);
            lastCtxStroomZipEntry = currentStroomZipEntry;
        }
    }

    public void closeDelete() {
        feedStreamTarget.values().forEach(streamStore::deleteStreamTarget);
        feedNestedStreamTarget.clear();
        feedStreamTarget.clear();
    }

    @Override
    public void close() {
        feedNestedStreamTarget.values().forEach(CloseableUtil::closeLogAndIgnoreException);
        feedStreamTarget.values().forEach(streamStore::closeStreamTarget);

        feedNestedStreamTarget.clear();
        feedStreamTarget.clear();
    }

    public void closeCurrentFeed() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("closeCurrentFeed() - " + currentFeedName);
        }
        CloseableUtil.closeLogAndIgnoreException(feedNestedStreamTarget.remove(currentFeedName));
        streamStore.closeStreamTarget(feedStreamTarget.remove(currentFeedName));
    }

    public Set<StreamEntity> getStreamSet() {
        return Collections.unmodifiableSet(streamSet);
    }

    private MetaMap getCurrentMetaMap() {
        if (currentMetaMap != null) {
            return currentMetaMap;
        }
        return globalMetaMap;
    }

    public NestedStreamTarget getCurrentNestedStreamTarget() throws IOException {
        NestedStreamTarget nestedStreamTarget = feedNestedStreamTarget.get(currentFeedName);

        if (nestedStreamTarget == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getCurrentNestedStreamTarget() - open stream for " + currentFeedName);
            }

            // Get the effective time if one has been provided.
            final Long effectiveMs = StreamFactory.getReferenceEffectiveTime(getCurrentMetaMap(), true);

            // Make sure the stream type is not null.
            if (currentStreamTypeName == null) {
                currentStreamTypeName = getStreamTypeName(currentFeedName);
            }

            final StreamProperties streamProperties = new StreamProperties.Builder()
                    .feedName(currentFeedName)
                    .streamTypeName(currentStreamTypeName)
                    .effectiveMs(effectiveMs)
                    .build();

            final StreamTarget streamTarget = streamStore.openStreamTarget(streamProperties);
            feedStreamTarget.put(currentFeedName, streamTarget);
            streamSet.add(streamTarget.getStream());
            nestedStreamTarget = new NestedStreamTarget(streamTarget, false);
            feedNestedStreamTarget.put(currentFeedName, nestedStreamTarget);
        }
        return nestedStreamTarget;
    }

}
