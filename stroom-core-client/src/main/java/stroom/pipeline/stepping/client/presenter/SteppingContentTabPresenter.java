/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.stepping.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.HasSave;
import stroom.core.client.HasSaveRegistry;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.data.client.presenter.ClassificationWrapperView;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

public class SteppingContentTabPresenter
        extends ContentTabPresenter<ClassificationWrapperView>
        implements HasSave, HasDirtyHandlers, CloseContentEvent.Handler {

    public static final String TAB_TYPE = "Stepping";
    private final SteppingPresenter steppingPresenter;
    private final HasSaveRegistry hasSaveRegistry;
    private DocRef pipeline;
    private boolean dirty;
    private boolean reading;
    private String lastLabel;

    @Inject
    public SteppingContentTabPresenter(final EventBus eventBus,
                                       final ClassificationWrapperView view,
                                       final SteppingPresenter steppingPresenter,
                                       final HasSaveRegistry hasSaveRegistry) {
        super(eventBus, view);
        this.steppingPresenter = steppingPresenter;
        this.hasSaveRegistry = hasSaveRegistry;
        hasSaveRegistry.register(this);

        view.setContent(steppingPresenter.getView());
        steppingPresenter.setTaskMonitorFactory(this);
    }

    @Override
    protected void onBind() {
        registerHandler(steppingPresenter.addDirtyHandler(event -> setDirty(event.isDirty())));
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (dirty && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        pipeline.getType() + " '" + pipeline.getName()
                                + "' has unsaved changes. Are you sure you want to close this item?",
                        result -> {
                            doClose(event, result);
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            doClose(event, true);
        }
    }

    private void doClose(final CloseContentEvent event, final boolean result) {
        event.getCallback().closeTab(result);
        if (result) {
            steppingPresenter.terminate();
            unbind();
            hasSaveRegistry.unregister(SteppingContentTabPresenter.this);
        }
    }


    public void read(final DocRef pipeline,
                     final StepType stepType,
                     final StepLocation stepLocation,
                     final Meta meta,
                     final String childStreamType) {
        reading = true;
        this.pipeline = pipeline;
        steppingPresenter.read(pipeline, stepType, stepLocation, meta, childStreamType);
        reading = false;
    }

    private void onDirty(final boolean dirty) {
        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }

    @Override
    public void save() {
        steppingPresenter.save();
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(final boolean dirty) {
        if (!reading) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);
            onDirty(dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.STEPPING_CIRCLE;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + pipeline.getName();
        }

        return pipeline.getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
