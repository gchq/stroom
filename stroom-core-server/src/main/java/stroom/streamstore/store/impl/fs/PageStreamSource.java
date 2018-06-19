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

package stroom.streamstore.store.impl.fs;

import stroom.entity.shared.PageRequest;
import stroom.streamstore.store.impl.fs.serializable.SegmentInputStream;

import java.io.IOException;

/**
 * Helper class for pages of data.
 */
public class PageStreamSource {
    private SegmentInputStream segmentInputStream;
    private PageRequest pageRequest;
    private Integer length;

    public PageStreamSource(final SegmentInputStream segmentInputStream, final PageRequest pageRequest,
                            final Integer length) {
        this.segmentInputStream = segmentInputStream;
        this.pageRequest = pageRequest;
        this.length = length;
    }

    public Long getOffset() {
        return pageRequest.getOffset();
    }

    public SegmentInputStream getSegmentInputStream() {
        return segmentInputStream;
    }

    public Long getTotal() {
        try {
            return segmentInputStream.count();
        } catch (IOException ioEx) {
            throw new RuntimeException(ioEx);
        }
    }

    public boolean isMore() {
        return getOffset().longValue() + getLength().longValue() < getTotal().longValue();
    }

    public Integer getLength() {
        return length;
    }
}
