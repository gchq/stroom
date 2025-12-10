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

package stroom.util.resultpage;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A result page builder that will receive results until we reach the requested page size.
 * When we have received enough values the add method will return false to inform the adding process that it should
 * stop.
 *
 * @param <T>
 */
public class ResultPageBuilder<T> {

    private final PageRequest pageRequest;
    private final List<T> values = new ArrayList<>();
    private boolean exact = true;
    private long count;

    public ResultPageBuilder(final PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest);
        Objects.requireNonNull(pageRequest.getOffset());
        Objects.requireNonNull(pageRequest.getLength());
        this.pageRequest = pageRequest;
    }

    public boolean add(final T t) {
        if (count >= pageRequest.getOffset()) {
            if (count < pageRequest.getOffset() + pageRequest.getLength()) {
                values.add(t);
            } else {
                exact = false;
                return false;
            }
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
        final PageResponse response = new PageResponse(pageRequest.getOffset(), values.size(), count, exact);
        return new ResultPage<>(values, response);
    }
}
