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

package stroom.streamtask.shared;

import stroom.entity.shared.Action;
import stroom.docref.DocRef;
import stroom.streamstore.meta.api.FindStreamCriteria;
import stroom.streamstore.shared.QueryData;

public class CreateProcessorAction extends Action<ProcessorFilter> {
    private static final long serialVersionUID = -1773544031158236156L;

    private DocRef pipeline;
    private QueryData queryData;
    private boolean enabled;
    private int priority;

    public CreateProcessorAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public CreateProcessorAction(final DocRef pipeline,
                                 final QueryData queryData,
                                 boolean enabled,
                                 int priority) {
        this.pipeline = pipeline;
        this.queryData = queryData;
        this.enabled = enabled;
        this.priority = priority;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public QueryData getQueryData() {
        return queryData;
    }

    public void setQueryData(final QueryData queryData) {
        this.queryData = queryData;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String getTaskName() {
        return "FetchProcessorAction - fetchProcessor()";
    }
}
