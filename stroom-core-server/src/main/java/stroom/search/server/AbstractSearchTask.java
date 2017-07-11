/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.server;

import stroom.query.api.v1.Query;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.util.shared.Task;
import stroom.util.task.ServerTask;

public abstract class AbstractSearchTask<R> extends ServerTask<R> {
    private final FindStreamCriteria streamFilter;
    private final Query query;

    public AbstractSearchTask(final Task<?> parentTask, final String userToken,
                              final FindStreamCriteria streamFilter, final Query query) {
        super(null, userToken);
        this.streamFilter = streamFilter;
        this.query = query;
    }

    public FindStreamCriteria getStreamFilter() {
        return streamFilter;
    }

    public Query getQuery() {
        return query;
    }
}