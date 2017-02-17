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

package stroom.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.api.TableResult;
import stroom.query.format.FieldFormatter;
import stroom.query.format.FormatterFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchResponseCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchResponseCreator.class);

    private final Store store;

    private final Map<String, ResultCreator> cachedResultCreators = new HashMap<>();

    // Cache the last results for each component.
    private final Map<String, Result> resultCache = new HashMap<>();

    public SearchResponseCreator(final Store store) {
        this.store = store;
    }

    public SearchResponse create(final SearchRequest searchRequest) {
        final SearchResponse searchResponse = new SearchResponse();
        final List<Result> results = new ArrayList<>(searchRequest.getResultRequests().length);

        // The result handler could possibly have not been set yet if the
        // AsyncSearchTask has not started execution.
        searchResponse.setHighlights(store.getHighlights());
        searchResponse.setComplete(store.isComplete());

        // Provide results if this search is incremental or the search is complete.
        if (searchRequest.incremental() || searchResponse.complete()) {
            // Copy the requested portion of the result cache into the result.
            for (final ResultRequest resultRequest : searchRequest.getResultRequests()) {
                final String componentId = resultRequest.getComponentId();

                // Only deliver data to components that actually want it.
                if (resultRequest.fetchData()) {
                    Result result = null;

                    final Data data = store.getData(componentId);
                    if (data != null) {
                        try {
                            final ResultCreator resultCreator = getResultCreator(componentId,
                                    resultRequest, searchRequest.getDateTimeLocale());
                            if (resultCreator != null) {
                                result = resultCreator.create(data, resultRequest);
                            }
                        } catch (final Exception e) {
                            final TableResult tableResult = new TableResult(componentId);
                            tableResult.setError(e.getMessage());
                            result = tableResult;
                        }
                    }

                    if (result != null) {
                        // Cache the new result and get the previous one.
                        final Result lastResult = resultCache.put(componentId, result);

                        // See if we have delivered an identical result before so we
                        // don't send more data to the client than we need to.
                        if (!result.equals(lastResult)) {
                            //
                            // CODE TO HELP DEBUGGING.
                            //

                            // try {
                            // if (lastComponentResult instanceof
                            // ChartResult) {
                            // final ChartResult lr = (ChartResult)
                            // lastComponentResult;
                            // final ChartResult cr = (ChartResult)
                            // componentResult;
                            // final File dir = new
                            // File(FileUtil.getTempDir());
                            // StreamUtil.stringToFile(lr.getJSON(), new
                            // File(dir, "last.json"));
                            // StreamUtil.stringToFile(cr.getJSON(), new
                            // File(dir, "current.json"));
                            // }
                            // } catch (final Exception e) {
                            // LOGGER.error(e.getMessage(), e);
                            // }

                            // Either we haven't returned a result before or this result
                            // is different from the one delivered previously so deliver it to the client.
                            results.add(result);
                            LOGGER.info("Delivering " + result + " for " + componentId);
                        }
                    }
                }
            }
        }

        if (results.size() > 0) {
            searchResponse.setResults(results.toArray(new Result[results.size()]));
        }

        // Deliver the latest results from the store.
        searchResponse.setErrors(store.getErrors());

        return searchResponse;
    }

    private ResultCreator getResultCreator(final String componentId,
                                           final ResultRequest resultRequest,
                                           final String dateTimeLocale) {
        if (cachedResultCreators.containsKey(componentId)) {
            return cachedResultCreators.get(componentId);
        }

        ResultCreator resultCreator = null;
        try {
            if (ResultStyle.TREE.equals(resultRequest.getResultStyle())) {
                resultCreator = new VisResultCreator(resultRequest, null, null);
            } else {
                final FieldFormatter fieldFormatter = new FieldFormatter(new FormatterFactory(dateTimeLocale));
                resultCreator = new TableResultCreator(fieldFormatter);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            cachedResultCreators.put(componentId, resultCreator);
        }

        return resultCreator;
    }

    public void destroy() {
        store.destroy();
    }
}