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

package stroom.pipeline.shared;

import stroom.streamstore.shared.StreamType;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;

import java.util.List;

public class FetchDataResult extends AbstractFetchDataResult {
    private static final long serialVersionUID = 7559713171858774241L;

    private String data;
    private boolean html;

    public FetchDataResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchDataResult(final StreamType streamType, final String classification,
                           final OffsetRange<Long> streamRange, final RowCount<Long> streamRowCount, final OffsetRange<Long> pageRange,
                           final RowCount<Long> pageRowCount, final List<StreamType> availableChildStreamTypes, final String data,
                           final boolean html) {
        super(streamType, classification, streamRange, streamRowCount, pageRange, pageRowCount,
                availableChildStreamTypes);
        this.data = data;
        this.html = html;
    }

    public String getData() {
        return data;
    }

    public boolean isHtml() {
        return html;
    }
}
