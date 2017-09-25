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

package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.internalstatistics.MetaDataStatistic;
import stroom.proxy.repo.MetaMapFactory;
import stroom.proxy.repo.StroomHeaderStreamHandler;
import stroom.proxy.repo.StroomStreamHandler;
import stroom.proxy.repo.StroomZipEntry;
import stroom.proxy.repo.StroomZipFileType;
import stroom.proxy.repo.StroomZipNameSet;
import stroom.streamstore.server.StreamFactory;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.server.StreamTarget;
import stroom.streamstore.server.fs.serializable.NestedStreamTarget;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.io.CloseableUtil;
import stroom.feed.StroomHeaderArguments;

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
    private final FeedService feedService;
    private final MetaDataStatistic metaDataStatistics;
    private final HashSet<Stream> streamSet;
    private final StroomZipNameSet stroomZipNameSet;
    private final Map<String, Feed> feedMap = new HashMap<>();
    private final Map<Feed, NestedStreamTarget> feedNestedStreamTarget = new HashMap<>();
    private final Map<Feed, StreamTarget> feedStreamTarget = new HashMap<>();
    private final ByteArrayOutputStream currentHeaderByteArrayOutputStream = new ByteArrayOutputStream();
    private boolean oneByOne;
    private StroomZipFileType currentFileType = null;
    private StroomZipEntry currentStroomZipEntry = null;
    private StroomZipEntry lastDatStroomZipEntry = null;
    private StroomZipEntry lastCtxStroomZipEntry = null;
    private Feed currentFeed;
    private StreamType currentStreamType;
    private MetaMap globalMetaMap;
    private MetaMap currentMetaMap;

    public StreamTargetStroomStreamHandler(final StreamStore streamStore, final FeedService feedService,
                                           final MetaDataStatistic metaDataStatistics, final Feed feed, final StreamType streamType) {
        this.streamStore = streamStore;
        this.feedService = feedService;
        this.metaDataStatistics = metaDataStatistics;
        this.currentFeed = feed;
        this.currentStreamType = streamType;
        this.streamSet = new HashSet<>();
        this.stroomZipNameSet = new StroomZipNameSet(true);
    }

    public static List<StreamTargetStroomStreamHandler> buildSingleHandlerList(final StreamStore streamStore,
                                                                               final FeedService feedService, final MetaDataStatistic metaDataStatistics, final Feed feed,
                                                                               final StreamType streamType) {
        final ArrayList<StreamTargetStroomStreamHandler> list = new ArrayList<>();
        list.add(new StreamTargetStroomStreamHandler(streamStore, feedService, metaDataStatistics, feed, streamType));
        return list;
    }

    public void setOneByOne(final boolean oneByOne) {
        this.oneByOne = oneByOne;
    }

    @Override
    public void handleHeader(final MetaMap metaMap) throws IOException {
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
            getCurrentNestedStreamTarget().getOutputStream(StreamType.CONTEXT).write(data, off, len);
        }
    }

    private Feed getFeed(final String name) throws IOException {
        Feed feed = feedMap.get(name);
        if (feed == null) {
            feed = feedService.loadByName(name);
            if (feed == null) {
                throw new IOException("Unable to get feed " + name);
            }
            feedMap.put(name, feed);
        }
        return feed;
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
            final String feed = currentMetaMap.get(StroomHeaderArguments.FEED);
            if (feed != null) {
                if (currentFeed == null || !currentFeed.getName().equals(feed)) {
                    // Yes ... load the new feed
                    currentFeed = getFeed(feed);
                    currentStreamType = currentFeed.getStreamType();

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

            getCurrentNestedStreamTarget().putNextEntry(StreamType.META);
            getCurrentNestedStreamTarget().getOutputStream(StreamType.META)
                    .write(currentHeaderByteArrayOutputStream.toByteArray());
            getCurrentNestedStreamTarget().closeEntry(StreamType.META);

        }
        if (StroomZipFileType.Data.equals(currentFileType)) {
            getCurrentNestedStreamTarget().closeEntry();
            lastDatStroomZipEntry = currentStroomZipEntry;
        }
        if (StroomZipFileType.Context.equals(currentFileType)) {
            getCurrentNestedStreamTarget().closeEntry(StreamType.CONTEXT);
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
        feedNestedStreamTarget.values().forEach(CloseableUtil::closeLogAndIngoreException);
        feedStreamTarget.values().forEach(streamStore::closeStreamTarget);

        feedNestedStreamTarget.clear();
        feedStreamTarget.clear();
    }

    public void closeCurrentFeed() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("closeCurrentFeed() - " + currentFeed);
        }
        CloseableUtil.closeLogAndIngoreException(feedNestedStreamTarget.remove(currentFeed));
        streamStore.closeStreamTarget(feedStreamTarget.remove(currentFeed));
    }

    @Override
    public void handleEntryStart(final StroomZipEntry stroomZipEntry) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("handleEntryStart() - " + stroomZipEntry);
        }

        currentFileType = stroomZipEntry.getStroomZipFileType();

        // We don't want to aggregate reference feeds.
        final boolean singleEntry = currentFeed.isReference() || oneByOne;

        final StroomZipEntry nextEntry = stroomZipNameSet.add(stroomZipEntry.getFullName());

        if (singleEntry && currentStroomZipEntry != null && !nextEntry.equalsBaseName(currentStroomZipEntry)) {
            // Close it if we have opened it.
            if (feedStreamTarget.containsKey(currentFeed)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("handleEntryStart() - Closing due to singleEntry=" + singleEntry + " " + currentFeed
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
            getCurrentNestedStreamTarget().putNextEntry(StreamType.CONTEXT);
        }

    }

    public Set<Stream> getStreamSet() {
        return Collections.unmodifiableSet(streamSet);
    }

    private MetaMap getCurrentMetaMap() {
        if (currentMetaMap != null) {
            return currentMetaMap;
        }
        return globalMetaMap;
    }

    public NestedStreamTarget getCurrentNestedStreamTarget() throws IOException {
        NestedStreamTarget nestedStreamTarget = feedNestedStreamTarget.get(currentFeed);

        if (nestedStreamTarget == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getCurrentNestedStreamTarget() - open stream for " + currentFeed);
            }

            // Get the effective time if one has been provided.
            final Long effectiveMs = StreamFactory.getReferenceEffectiveTime(getCurrentMetaMap(), true);

            // Make sure the stream type is not null.
            if (currentStreamType == null) {
                currentStreamType = currentFeed.getStreamType();
            }

            final Stream stream = Stream.createStream(currentStreamType, currentFeed, effectiveMs);

            final StreamTarget streamTarget = streamStore.openStreamTarget(stream);
            feedStreamTarget.put(currentFeed, streamTarget);
            streamSet.add(streamTarget.getStream());
            nestedStreamTarget = new NestedStreamTarget(streamTarget, false);
            feedNestedStreamTarget.put(currentFeed, nestedStreamTarget);
        }
        return nestedStreamTarget;
    }

}
