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

package stroom.process.shared;

import stroom.entity.shared.Action;
import stroom.query.api.v1.DocRef;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamtask.shared.StreamProcessorFilter;

public class CreateProcessorAction extends Action<StreamProcessorFilter> {
    private static final long serialVersionUID = -1773544031158236156L;

    private DocRef pipeline;
    private FindStreamCriteria findStreamCriteria;
    private boolean enabled;
    private int priority;

    public CreateProcessorAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public CreateProcessorAction(final DocRef pipeline, final FindStreamCriteria findStreamCriteria, boolean enabled,
                                 int priority) {
        this.pipeline = pipeline;
        this.findStreamCriteria = findStreamCriteria;
        this.enabled = enabled;
        this.priority = priority;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public FindStreamCriteria getFindStreamCriteria() {
        return findStreamCriteria;
    }

    public void setFindStreamCriteria(final FindStreamCriteria findStreamCriteria) {
        this.findStreamCriteria = findStreamCriteria;
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
