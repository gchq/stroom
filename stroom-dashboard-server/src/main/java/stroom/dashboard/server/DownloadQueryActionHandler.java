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

package stroom.dashboard.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.shared.DownloadQueryAction;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.servlet.SessionResourceStore;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
@TaskHandlerBean(task = DownloadQueryAction.class)
@Scope(value = StroomScope.TASK)
class DownloadQueryActionHandler extends AbstractTaskHandler<DownloadQueryAction, ResourceGeneration> {

    private transient static final Logger LOGGER = LoggerFactory.getLogger(DownloadQueryActionHandler.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final SearchRequestMapper searchRequestMapper;
    private final SessionResourceStore sessionResourceStore;

    @Inject
    DownloadQueryActionHandler(final SearchRequestMapper searchRequestMapper,
                               final SessionResourceStore sessionResourceStore) {
        this.searchRequestMapper = searchRequestMapper;
        this.sessionResourceStore = sessionResourceStore;
    }

    @Override
    public ResourceGeneration exec(final DownloadQueryAction action) {

        try {
            if (action.getSearchRequest() == null) {
                throw new EntityServiceException("Query is empty");
            }
            stroom.query.api.v1.SearchRequest apiSearchRequest = searchRequestMapper.mapRequest(
                    action.getDashboardQueryKey(),
                    action.getSearchRequest());

            if (apiSearchRequest == null) {
                throw new EntityServiceException("Query could not be mapped to a SearchRequest");
            }

            //generate the export file
            String fileName = action.getDashboardQueryKey().toString();
            fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
            fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
            fileName = fileName + ".json";

            ResourceKey resourceKey = sessionResourceStore.createTempFile(fileName);
            final Path outputFile = sessionResourceStore.getTempFile(resourceKey);

            JsonUtil.writeValue(outputFile, apiSearchRequest);

            return new ResourceGeneration(resourceKey, new ArrayList<>());
        } catch (final Exception ex) {
            throw EntityServiceExceptionUtil.create(ex);
        }
    }
}
