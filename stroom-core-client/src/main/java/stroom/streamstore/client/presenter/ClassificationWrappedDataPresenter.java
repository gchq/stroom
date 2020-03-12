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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.streamstore.shared.StreamType;

public class ClassificationWrappedDataPresenter extends ClassificationWrapperPresenter implements BeginSteppingHandler {
    private final DataPresenter dataPresenter;
    private SourceLocation sourceLocation;

    @Inject
    public ClassificationWrappedDataPresenter(final EventBus eventBus, final ClassificationWrapperView view,
                                              final DataPresenter dataPresenter) {
        super(eventBus, view);
        this.dataPresenter = dataPresenter;
        dataPresenter.setClassificationUiHandlers(this);
        dataPresenter.setBeginSteppingHandler(this);

        setInSlot(ClassificationWrapperView.CONTENT, dataPresenter);
    }

    public void fetchData(final SourceLocation sourceLocation) {
        dataPresenter.fetchData(sourceLocation);
        this.sourceLocation = sourceLocation;
    }

    public void clear() {
        dataPresenter.clear();
        this.sourceLocation = null;
    }

    public void setFormatOnLoad(final boolean formatOnLoad) {
        dataPresenter.setFormatOnLoad(formatOnLoad);
    }

    @Override
    public void beginStepping(final Long streamId, final StreamType childStreamType) {
        if (streamId != null) {
            BeginPipelineSteppingEvent.fire(this, streamId, null, childStreamType, new StepLocation(streamId, sourceLocation.getPartNo(), sourceLocation.getRecordNo()), null);
        }
    }
}
