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

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.meta.shared.Meta;
import stroom.util.pipeline.scope.PipelineScoped;

@PipelineScoped
public class MetaHolder implements Holder {
    private InputStreamProvider inputStreamProvider;

    private Meta meta;
    private String childStreamType;
    private long streamNo;

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(final Meta meta) {
        this.meta = meta;
    }

    public String getChildStreamType() {
        return childStreamType;
    }

    public void setChildStreamType(final String childStreamType) {
        if (!childStreamType.equals(StreamTypeNames.META) && !childStreamType.equals(StreamTypeNames.CONTEXT)) {
            this.childStreamType = childStreamType;
        }
    }

    public void setInputStreamProvider(final InputStreamProvider inputStreamProvider) {
        this.inputStreamProvider = inputStreamProvider;
    }

    public InputStreamProvider getInputStreamProvider() {
        return inputStreamProvider;
    }

    public long getStreamNo() {
        return streamNo;
    }

    public void setStreamNo(final long streamNo) {
        this.streamNo = streamNo;
    }
}
