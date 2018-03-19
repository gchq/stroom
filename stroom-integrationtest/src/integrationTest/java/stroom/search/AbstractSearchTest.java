/*
 * Copyright 2017 Crown Copyright
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

package stroom.search;

import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.search.server.SearchResultCreatorManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;

public abstract class AbstractSearchTest extends AbstractCoreIntegrationTest {

    @Resource
    private SearchResultCreatorManager searchResultCreatorManager;

    protected SearchResponse search(SearchRequest searchRequest) {
        final SearchResponseCreator searchResponseCreator = searchResultCreatorManager.get(
                new SearchResultCreatorManager.Key(searchRequest));

        SearchResponse response = searchResponseCreator.create(searchRequest);
        try {
            while (!response.complete()) {
                response = searchResponseCreator.create(searchRequest);

                if (!response.complete()) {
                    ThreadUtil.sleep(1000);
                }
            }
        } finally {
            searchResultCreatorManager.remove(new SearchResultCreatorManager.Key(searchRequest.getKey()));
        }

        return response;
    }
}
