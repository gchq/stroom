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

import stroom.io.StreamCloser;
import stroom.streamstore.api.StreamSource;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProviderImpl;
import stroom.streamstore.shared.Stream;
import stroom.guice.PipelineScoped;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@PipelineScoped
public class StreamHolder implements Holder {
    private final Map<String, StreamSourceInputStreamProvider> streamProviders = new HashMap<>();

    private final StreamCloser streamCloser;

    private Stream stream;
    private long streamNo;

    @Inject
    public StreamHolder(final StreamCloser streamCloser) {
        this.streamCloser = streamCloser;
    }

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
            streamProviders.put(source.getStreamTypeName(), provider);
        }
    }

    public void addProvider(final StreamSourceInputStreamProvider provider, final String streamType) {
        if (provider != null) {
            streamCloser.add(provider);
            streamProviders.put(streamType, provider);
        }
    }

    public StreamSourceInputStreamProvider getProvider(final String streamType) {
        return streamProviders.get(streamType);
    }

    public long getStreamNo() {
        return streamNo;
    }

    public void setStreamNo(final long streamNo) {
        this.streamNo = streamNo;
    }
}
