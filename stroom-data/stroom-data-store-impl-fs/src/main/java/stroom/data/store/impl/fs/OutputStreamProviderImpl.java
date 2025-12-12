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

package stroom.data.store.impl.fs;

import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.impl.fs.standard.SegmentOutputStreamProvider;
import stroom.data.store.impl.fs.standard.SegmentOutputStreamProviderFactory;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

/**
 * Used by {@link stroom.data.store.impl.fs.shared.FsVolumeType#STANDARD}
 */
public class OutputStreamProviderImpl implements OutputStreamProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OutputStreamProviderImpl.class);

    private final Meta meta;
    private final SegmentOutputStreamProviderFactory factory;
    private final SegmentOutputStreamProvider root;
    private final long index;

    public OutputStreamProviderImpl(final Meta meta,
                                    final SegmentOutputStreamProviderFactory factory,
                                    final long index) {
        this.meta = meta;
        this.factory = factory;
        this.index = index;
        root = factory.getSegmentOutputStreamProvider(null);
    }

    @Override
    public SegmentOutputStream get() {
        LOGGER.debug(() -> LogUtil.message("get() - metaId: {}, index: {}", meta.getId(), index));
        return root.get(index);
    }

    @Override
    public SegmentOutputStream get(final String streamTypeName) {
        if (streamTypeName == null) {
            return get();
        }

        LOGGER.debug(() -> LogUtil.message("get() - streamTypeName: {}, metaId: {}, index: {}",
                streamTypeName, meta.getId(), index));

        final SegmentOutputStreamProvider segmentOutputStreamProvider = factory.getSegmentOutputStreamProvider(
                streamTypeName);
        if (segmentOutputStreamProvider == null) {
            return null;
        }
        return segmentOutputStreamProvider.get(index);
    }

    @Override
    public void close() {
//        for (SegmentOutputStreamProvider nestedOutputStream : nestedOutputStreamMap.values()) {
//            nestedOutputStream.nestedOutputStream.close();
//        }
    }
}
