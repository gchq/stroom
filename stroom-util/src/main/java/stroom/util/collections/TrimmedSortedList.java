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

package stroom.util.collections;

import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TrimmedSortedList<T> {

    private final PageRequest pageRequest;
    private final int maxSize;
    private final int trimSize;
    private final Comparator<T> comparator;

    private List<T> list = new ArrayList<>();
    private long total;

    public TrimmedSortedList(final PageRequest pageRequest,
                             final Comparator<T> comparator) {
        this.pageRequest = pageRequest;
        this.comparator = comparator;
        maxSize = Math.max(0, pageRequest.getOffset() + pageRequest.getLength() + 1);
        trimSize = maxSize + 10000;
    }

    public void add(final T t) {
        list.add(t);
        total++;
        if (list.size() > trimSize) {
            list.sort(comparator);
            list = list.subList(0, maxSize);
        }
    }

    public List<T> getList() {
        list.sort(comparator);
        if (list.size() > maxSize) {
            list = list.subList(0, maxSize);
        }
        return list;
    }

    public ResultPage<T> getResultPage() {
        if (list.size() < pageRequest.getOffset()) {
            return new ResultPage<>(Collections.emptyList(), PageResponse
                    .builder()
                    .offset(pageRequest.getOffset())
                    .length(0)
                    .total(total)
                    .exact(true)
                    .build());
        }

        list.sort(comparator);
        final List<T> trimmed = list
                .subList(
                        pageRequest.getOffset(),
                        Math.min(pageRequest.getOffset() + pageRequest.getLength(), list.size()));
        return new ResultPage<>(trimmed, PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(trimmed.size())
                .total(total)
                .exact(true)
                .build());
    }
}
