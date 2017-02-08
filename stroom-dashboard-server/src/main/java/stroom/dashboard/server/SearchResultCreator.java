/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.dashboard.server;

import org.springframework.stereotype.Component;
import stroom.dashboard.server.format.FieldFormatter;
import stroom.dashboard.server.format.FormatterFactory;
import stroom.dashboard.server.vis.VisComponentResultCreator;
import stroom.query.ResultStore;
import stroom.query.SearchResultCollector;
import stroom.query.shared.ComponentResult;
import stroom.query.shared.ComponentResultRequest;
import stroom.query.shared.SearchRequest;
import stroom.query.shared.SearchResponse;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.EqualsUtil;
import stroom.visualisation.shared.VisualisationService;

import javax.inject.Inject;
import java.util.Map.Entry;

@Component
public class SearchResultCreator {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SearchResultCreator.class);

    private final VisualisationService visualisationService;

    @Inject
    public SearchResultCreator(final VisualisationService visualisationService) {
        this.visualisationService = visualisationService;
    }

    public SearchResponse createResult(final ActiveQuery activeQuery, final SearchRequest searchRequest) {
        final SearchResultCollector collector = activeQuery.getSearchResultCollector();
        final SearchResponse result = new SearchResponse();

        // The result handler could possibly have not been set yet if the
        // AsyncSearchTask has not started execution.
        result.setHighlights(collector.getHighlights());
        result.setComplete(collector.isComplete());

        // Provide results if this search is incremental or the search is complete.
        if (searchRequest.getSearch().isIncremental() || result.isComplete()) {
            // Copy the requested portion of the result cache into the result.
            for (final Entry<String, ComponentResultRequest> entry : searchRequest.getComponentResultRequests()
                    .entrySet()) {
                final String componentId = entry.getKey();
                final ComponentResultRequest componentResultRequest = entry.getValue();

                // Only deliver data to components that actually want it.
                if (componentResultRequest.wantsData()) {
                    ComponentResult componentResult = null;

                    final ResultStore resultStore = collector.getResultStore(componentId);
                    if (resultStore != null) {
                        try {
                            final ComponentResultCreator componentResultCreator = getComponentResultCreator(componentId,
                                    componentResultRequest, activeQuery, searchRequest.getSearch().getDateTimeLocale());
                            if (componentResultCreator != null) {
                                componentResult = componentResultCreator.create(resultStore, componentResultRequest);
                            }
                        } catch (final Exception e) {
                            componentResult = new ComponentResult() {
                                @Override
                                public String toString() {
                                    return e.getMessage();
                                }
                            };
                        }
                    }

                    // See if we have delivered an identical result before so we
                    // don't send more data to the client than we need to.
                    boolean returnResult = false;
                    if (!activeQuery.getLastResults().containsKey(componentId)) {
                        returnResult = true;
                    } else {
                        final ComponentResult lastComponentResult = activeQuery.getLastResults().get(componentId);
                        if (!EqualsUtil.isEquals(componentResult, lastComponentResult)) {
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

                            returnResult = true;
                        }
                    }

                    if (returnResult) {
                        // Either we haven't returned a result before or this result
                        // is different from the one delivered previously so store
                        // this and deliver it to the client.
                        activeQuery.getLastResults().put(componentId, componentResult);

                        result.addResult(componentId, componentResult);
                        LOGGER.info("Delivering " + componentResult + " for " + componentId);
                    }
                }
            }
        }

        // Deliver the latest results from the collector.
        result.setErrors(collector.getErrors());

        return result;
    }

    private ComponentResultCreator getComponentResultCreator(final String componentId,
            final ComponentResultRequest componentResultRequest, final ActiveQuery activeQuery,
            final String dateTimeLocale) {
        if (activeQuery.getComponentResultCreatorMap().containsKey(componentId)) {
            return activeQuery.getComponentResultCreatorMap().get(componentId);
        }

        ComponentResultCreator componentResultCreator = null;
        try {
            switch (componentResultRequest.getComponentType()) {
            case TABLE:
                final FieldFormatter fieldFormatter = new FieldFormatter(new FormatterFactory(dateTimeLocale));
                componentResultCreator = new TableComponentResultCreator(fieldFormatter);
                break;
            case VIS:
                componentResultCreator = VisComponentResultCreator.create(visualisationService, componentResultRequest);
                break;
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            activeQuery.getComponentResultCreatorMap().put(componentId, componentResultCreator);
        }

        return componentResultCreator;
    }
}
