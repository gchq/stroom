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

package stroom.pipeline.state;

import java.io.IOException;

import javax.annotation.Resource;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import stroom.streamstore.server.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.server.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.StreamType;
import stroom.util.spring.StroomScope;
import stroom.feed.MetaMap;

@Component
@Scope(value = StroomScope.TASK)
public class MetaDataHolder extends AbstractHolder<MetaDataHolder>implements Holder {
    private static final int MINIMUM_BYTE_COUNT = 10;

    @Resource
    private StreamHolder streamHolder;
    private MetaMap metaData;
    private long lastMetaStreamNo;

    public MetaMap getMetaData() throws IOException {
        // Determine if we need to read the meta stream.
        if (metaData == null || lastMetaStreamNo != streamHolder.getStreamNo()) {
            metaData = new MetaMap();
            lastMetaStreamNo = streamHolder.getStreamNo();

            // Setup meta data.
            final StreamSourceInputStreamProvider provider = streamHolder.getProvider(StreamType.META);
            if (provider != null) {
                // Get the input stream.
                final StreamSourceInputStream inputStream = provider.getStream(lastMetaStreamNo);

                // Make sure we got an input stream.
                if (inputStream != null) {
                    // Only use meta data if we actually have some.
                    final long byteCount = inputStream.size();
                    if (byteCount > MINIMUM_BYTE_COUNT) {
                        metaData.read(inputStream, false);
                    }
                }
            }
        }

        return metaData;
    }
}
