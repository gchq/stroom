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

package stroom.data.client.presenter;

import stroom.pipeline.shared.SourceLocation;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class ClassificationWrappedSourcePresenter
        extends ClassificationWrapperPresenter {

    private final SourcePresenter sourcePresenter;
    private SourceLocation sourceLocation;

    @Inject
    public ClassificationWrappedSourcePresenter(final EventBus eventBus,
                                                final ClassificationWrapperView view,
                                                final SourcePresenter sourcePresenter) {
        super(eventBus, view);
        this.sourcePresenter = sourcePresenter;
        sourcePresenter.setClassificationUiHandlers(this);

        setInSlot(ClassificationWrapperView.CONTENT, sourcePresenter);
    }

    public void clear() {
        sourcePresenter.clear();
        this.sourceLocation = null;
    }

    public void setSourceLocation(final SourceLocation sourceLocation, final boolean force) {
        sourcePresenter.setSourceLocation(sourceLocation, force);
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        sourcePresenter.setSourceLocation(sourceLocation);
    }

    public void setSteppingSource(final boolean isSteppingSource) {
        sourcePresenter.setSteppingSource(isSteppingSource);
    }

}
