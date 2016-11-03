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
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import stroom.io.StreamCloser;
import stroom.streamstore.server.StreamSource;
import stroom.streamstore.server.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.server.fs.serializable.StreamSourceInputStreamProviderImpl;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;

@Component
@Scope(StroomScope.TASK)
public class StreamHolder implements Holder {
    @Resource
    private StreamCloser streamCloser;

    private Stream stream;
    private final Map<StreamType, StreamSourceInputStreamProvider> streamProviders = new HashMap<>();
    private long streamNo;

    public Stream getStream() {
        return stream;
    }

    public void setStream(final Stream stream) {
        this.stream = stream;
    }

    public void addProvider(final StreamSource source) throws IOException {
        if (source != null) {
            final StreamSourceInputStreamProvider provider = new StreamSourceInputStreamProviderImpl(source);
            streamCloser.add(provider);
            streamCloser.add(source);
            streamProviders.put(source.getType(), provider);
        }
    }

    public void addProvider(final StreamSourceInputStreamProvider provider, final StreamType streamType)
            throws IOException {
        if (provider != null) {
            streamCloser.add(provider);
            streamProviders.put(streamType, provider);
        }
    }

    public StreamSourceInputStreamProvider getProvider(final StreamType streamType) {
        return streamProviders.get(streamType);
    }

    public void setStreamNo(final long streamNo) {
        this.streamNo = streamNo;
    }

    public long getStreamNo() {
        return streamNo;
    }
}
