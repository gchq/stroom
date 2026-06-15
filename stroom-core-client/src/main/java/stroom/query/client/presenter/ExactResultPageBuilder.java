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

package stroom.query.client.presenter;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A page request constrained result page builder that will receive results and track the total results added.
 *
 * @param <T>
 */
public class ExactResultPageBuilder<T> {

    private final PageRequest pageRequest;
    private final List<T> values = new ArrayList<>();
    private long count;

    public ExactResultPageBuilder(final PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest);
        Objects.requireNonNull(pageRequest.getOffset());
        Objects.requireNonNull(pageRequest.getLength());
        this.pageRequest = pageRequest;
    }

    public boolean add(final T t) {
        if (count >= pageRequest.getOffset() && count < pageRequest.getOffset() + pageRequest.getLength()) {
            values.add(t);
        }
        count++;
        return true;
    }

    public void skip(final int count) {
        this.count += count;
    }

    public int size() {
        return values.size();
    }

    public ResultPage<T> build() {
        final PageResponse response = new PageResponse(pageRequest.getOffset(), values.size(), count, true);
        return new ResultPage<>(values, response);
    }
}
