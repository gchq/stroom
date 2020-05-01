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

package stroom.util.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * List that knows how big the whole set is.
 */
@JsonInclude(Include.NON_NULL)
public class ResultPage<T> implements Serializable {
    @JsonProperty
    private final List<T> values;
    @JsonProperty
    private final PageResponse pageResponse;

    public ResultPage(final List<T> values) {
        this.values = values;
        this.pageResponse = createPageResponse(values);
    }

    @JsonCreator
    public ResultPage(@JsonProperty("values") final List<T> values,
                      @JsonProperty("pageResponse") final PageResponse pageResponse) {
        this.values = values;
        this.pageResponse = pageResponse;
    }

    /**
     * Creates a list limited to a result page from a full list of results.
     *
     * @param fullList    The full list of results to create the result page from.
     * @param pageRequest The page request to limit the result list by.
     * @param <T>         The type of list item.
     * @return A list limited to a result page from a full list of results.
     */
    public static <T> ResultPage<T> createPageLimitedList(final List<T> fullList, final PageRequest pageRequest) {
        if (pageRequest != null) {
            int offset = 0;
            if (pageRequest.getOffset() != null) {
                offset = pageRequest.getOffset().intValue();
            }

            int length = fullList.size() - offset;
            if (pageRequest.getLength() != null) {
                length = Math.min(length, pageRequest.getLength());
            }

            // If the page request will lead to a limited number of results then apply that limit here.
            if (offset != 0 || length < fullList.size()) {
                // Ideally we'd use List.subList here but can't as GWT can't serialise the returned list type.
//                final List<T> limited = fullList.subList(offset, offset + length);
                final List<T> limited = new ArrayList<>(length);
                for (int i = offset; i < offset + length; i++) {
                    limited.add(fullList.get(i));
                }

                final PageResponse pageResponse = new PageResponse((long) offset, limited.size(), (long) fullList.size(), true);
                return new ResultPage<>(limited, pageResponse);
            }
        }

        final PageResponse pageResponse = new PageResponse(0L, fullList.size(), (long) fullList.size(), true);
        return new ResultPage<>(fullList, pageResponse);
    }

    /**
     * Used for full queries (not bounded).
     */
    public static <T> ResultPage<T> createUnboundedList(final List<T> realList) {
        if (realList != null) {
            return new ResultPage<>(realList, createPageResponse(realList));
        } else {
            return new ResultPage<>(Collections.emptyList());
        }
    }

    public static PageResponse createPageResponse(final List<?> values) {
        if (values != null) {
            return new PageResponse(0L, values.size(), (long) values.size(), true);
        }
        return new PageResponse(0L, 0, 0L, true);
    }

    public static PageResponse createPageResponse(final List<?> values, final PageResponse pageResponse) {
        if (values != null) {
            return new PageResponse(pageResponse.getOffset(), values.size(), pageResponse.getTotal(), pageResponse.isExact());
        }
        return new PageResponse(pageResponse.getOffset(), 0, pageResponse.getTotal(), pageResponse.isExact());
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    public static <T> ResultPage<T> createCriterialBasedList(final List<T> realList,
                                                             final BaseCriteria baseCriteria) {
        return createPageResultList(realList, baseCriteria.getPageRequest(), null);
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    public static <T> ResultPage<T> createCriterialBasedList(final List<T> realList,
                                                             final BaseCriteria baseCriteria, final Long totalSize) {
        return createPageResultList(realList, baseCriteria.getPageRequest(), totalSize);
    }

    /**
     * Used for filter queries (maybe bounded).
     */
    private static <T> ResultPage<T> createPageResultList(final List<T> realList,
                                                         final PageRequest pageRequest,
                                                         final Long totalSize) {
        final boolean limited = pageRequest != null && pageRequest.getLength() != null;
        boolean moreToFollow = false;
        Long calulatedTotalSize = totalSize;
        long offset = 0;
        if (pageRequest != null && pageRequest.getOffset() != null) {
            offset = pageRequest.getOffset();
        }

        if (limited) {
            // All our queries are + 1 on the limit so that we know there is
            // more to come
            final int overflow = realList.size() - pageRequest.getLength();
            moreToFollow = overflow > 0;

            if (moreToFollow) {
                if (overflow > 1) {
                    // Here we check that if the query was supposed to be limited
                    // make sure we have
                    // get to process more that 1 + that limit. If this fails it
                    // will be a coding error
                    // or not applying the limit.
                    throw new IllegalStateException(
                            "For some reason we returned more rows that we were limited to. Did you apply the restriction criteria?");
                }

                // All our queries are + 1 to we need to remove the last element
                realList.remove(realList.size() - 1);

            } else if (totalSize == null) {
                // If we have not been given the total size see if we can work it out
                // based on hitting the end
                calulatedTotalSize = pageRequest.getOffset() + realList.size();
            }
        }

        final PageResponse pageResponse = new PageResponse(offset, realList.size(), calulatedTotalSize, !moreToFollow);
        return new ResultPage<>(realList, pageResponse);
    }

    public PageResponse getPageResponse() {
        return pageResponse;
    }

    public List<T> getValues() {
        return values;
    }

    public int size() {
        return values.size();
    }

    /**
     * @return the first item or null if the list is empty
     */
    @JsonIgnore
    public T getFirst() {
        if (values.size() > 0) {
            return values.get(0);
        } else {
            return null;
        }
    }

    @JsonIgnore
    public int getPageStart() {
        return (int) pageResponse.getOffset();
    }

    @JsonIgnore
    public int getPageSize() {
        if (pageResponse.getTotal() == null) {
            return getPageStart() + values.size();
        }
        return pageResponse.getTotal().intValue();
    }

    @JsonIgnore
    public boolean isExact() {
        return pageResponse.isExact();
    }

    public Stream<T> stream() {
        return values.stream();
    }

    public Stream<T> parallelStream() {
        return values.parallelStream();
    }

    public void forEach(final Consumer<? super T> action) {
        values.forEach(action);
    }

    private static <T, R extends ResultPage<T>> Collector<T, List<T>, R> createCollector(
            final PageRequest pageRequest,
            final BiFunction<List<T>, PageResponse, R> resultPageFactory) {

        // Explicit typing needed for GWT
        return new Collector<T, List<T>, R>() {
            long counter = 0L;

            @Override
            public Supplier<List<T>> supplier() {
                return ArrayList::new;
            }

            @Override
            public BiConsumer<List<T>, T> accumulator() {
                return (accumulator, item) -> {
                    if (pageRequest == null
                            || (
                            counter >= pageRequest.getOffset()
                                    && accumulator.size() < pageRequest.getLength())) {

                        accumulator.add(item);
                    }
                    counter++;
                };
            }

            @Override
            public BinaryOperator<List<T>> combiner() {
                return (left, right) -> {
                    left.addAll(right);
                    return left;
                };
            }

            @Override
            public Function<List<T>, R> finisher() {
                return accumulator ->
                        resultPageFactory.apply(
                                accumulator,
                                new PageResponse(
                                        pageRequest != null ? pageRequest.getOffset() : 0,
                                        accumulator.size(),
                                        counter,
                                        true));
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    /**
     * Creates a collector that builds a sub class of a ResultPage, e.g.
     *   .collect(ListConfigResponse.collector(pageRequest, ListConfigResponse::new));
     */
    public static <T, R extends ResultPage<T>> Collector<T, List<T>, R> collector(
            final PageRequest pageRequest,
            final BiFunction<List<T>, PageResponse, R> resultPageFactory) {

        return createCollector(pageRequest, resultPageFactory);
    }

    /**
     * Creates a collector that builds a sub class of a ResultPage, e.g.
     *   .collect(ListConfigResponse.collector(ListConfigResponse::new));
     */
    public static <T, R extends ResultPage<T>> Collector<T, List<T>, R> collector(
            final BiFunction<List<T>, PageResponse, R> resultPageFactory) {

        return createCollector(null, resultPageFactory);
    }

    public static <T> Collector<T, List<T>, ResultPage<T>> collector(final PageRequest pageRequest) {
        return createCollector(pageRequest, ResultPage::new);
    }

    public static <T, R extends ResultPage<T>> BinaryOperator<R> reducer(final Function<List<T>, R> resultPageFactory,
                                                                         final Class<T> itemType) {
        return (final R resultPage1, final R resultPage2) -> {
            final List<T> combinedList = new ArrayList<>();
            combinedList.addAll(resultPage1.getValues());
            combinedList.addAll(resultPage2.getValues());
            return resultPageFactory.apply(combinedList);
        };
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ResultPage<?> that = (ResultPage<?>) o;
        return Objects.equals(values, that.values) &&
            Objects.equals(pageResponse, that.pageResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values, pageResponse);
    }

    @Override
    public String toString() {
        return "ResultPage{" +
            "values=" + values +
            ", pageResponse=" + pageResponse +
            '}';
    }
}
