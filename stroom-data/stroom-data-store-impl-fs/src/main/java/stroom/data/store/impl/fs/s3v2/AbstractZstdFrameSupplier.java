/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntSortedSets;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Objects;

public abstract class AbstractZstdFrameSupplier implements ZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractZstdFrameSupplier.class);

    // TODO needs to come from config
    // If we need a critical mass of the stream we might as well download it all to a temp file and
    // grab the bits we need from there.  Maybe it should be based on % of total frame count rather than
    // on size as transfer cost is free, but requests have a cost.
    protected static final double DOWNLOAD_ALL_PCT_THRESHOLD = 50;

    protected ZstdSeekTable zstdSeekTable = null;
    protected IntSortedSet includedFrameIndexes = IntSortedSets.emptySet();
    protected boolean includeAll = false;
    protected FrameLocation currentFrameLocation = null;

    private Iterator<FrameLocation> frameLocationIterator = null;

    @Override
    public void initialise(final ZstdSeekTable zstdSeekTable,
                           final IntSortedSet includedFrameIndexes,
                           final boolean includeAll) {

        LOGGER.debug("initialise() - zstdSeekTable: {}, includeAll: {}", zstdSeekTable, includeAll);
        if (includeAll) {
            if (NullSafe.hasItems(includedFrameIndexes)) {
                throw new IllegalArgumentException("Cannot set includeAll and includedFrameIndexes");
            }
        }
        if (this.zstdSeekTable != null) {
            throw new IllegalStateException("Already initialised");
        }
        this.zstdSeekTable = Objects.requireNonNull(zstdSeekTable);
        this.includeAll = includeAll;
        this.includedFrameIndexes = Objects.requireNonNullElseGet(
                includedFrameIndexes,
                IntSortedSets::emptySet);
        this.frameLocationIterator = includeAll
                ? zstdSeekTable.iterator()
                : zstdSeekTable.iterator(includedFrameIndexes);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove not supported");
    }

    @Override
    public boolean hasNext() {
        LOGGER.debug("hasNext()");
        checkInitialised();
        final boolean hasNext = frameLocationIterator.hasNext();
        LOGGER.debug("hasNext() - currentFrameLocation: {}, returning: {}", currentFrameLocation, hasNext);
        return hasNext;
    }

    protected FrameLocation nextFrameLocation() {
        final FrameLocation frameLocation = frameLocationIterator.next();
        LOGGER.debug("nextFrameLocation() - frameLocation: {}", frameLocation);
        this.currentFrameLocation = frameLocation;
        return frameLocation;
    }

    @Override
    public abstract InputStream next();

    @Override
    public FrameLocation getCurrentFrameLocation() {
        return currentFrameLocation;
    }

    public abstract void close() throws Exception;


    protected long getTotalUncompressedSize(final IntSortedSet includedFrameIndexes,
                                            final boolean includeAll) {
        if (includeAll) {
            return zstdSeekTable.getTotalUncompressedSize();
        } else {
            return zstdSeekTable.getTotalUncompressedSize(includedFrameIndexes);
        }
    }

    protected void checkInitialised() {
        if (zstdSeekTable == null) {
            throw new IllegalStateException("Not initialised");
        }
    }
}
