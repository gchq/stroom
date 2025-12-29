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

package stroom.data.store.impl.fs.s3v2;


import it.unimi.dsi.fastutil.ints.IntSortedSet;

import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

public interface ZstdFrameSupplier extends AutoCloseable, Iterator<InputStream> {

    /**
     * Allows the {@link ZstdFrameSupplier} implementation to make decisions on how best to provide
     * the requested {@link InputStream}s returned by {@link ZstdFrameSupplier#next()}
     * <p>
     * <strong>MUST</strong> be called before {@link ZstdFrameSupplier#next()} or {@link ZstdFrameSupplier#hasNext()}
     * are called.
     * </p>
     *
     * @param zstdSeekTable        The {@link ZstdSeekTable} that provides the locations of all the frames.
     * @param includedFrameIndexes The set of frames that can be supplied.
     * @param includeAll           If true, all available frames will be supplied by repeated calls to
     *                             {@link ZstdFrameSupplier#next()}. If true and includedFrameIndexes is not
     *                             empty, an exception will be thrown. If false, only those frames included
     *                             in includedFrameIndexes will be supplied by calls
     *                             to {@link ZstdFrameSupplier#next()}.
     */
    void initialise(final ZstdSeekTable zstdSeekTable,
                    final IntSortedSet includedFrameIndexes,
                    final boolean includeAll);

    /**
     * Returns {@code true} if another frame {@link InputStream} can be supplied.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Override
    boolean hasNext();

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     */
    @Override
    InputStream next();

    /**
     * @return The {@link FrameLocation} returned by the last call to {@link ZstdFrameSupplier#next()}
     * or null if {@link ZstdFrameSupplier#next()} has not been called yet.
     */
    FrameLocation getCurrentFrameLocation();
}
