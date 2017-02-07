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

package stroom.pipeline.stepping.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.alert.client.event.ConfirmEvent;
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.app.client.ContentManager.CloseCallback;
import stroom.app.client.ContentManager.CloseHandler;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.shared.DocRef;
import stroom.streamstore.client.presenter.ClassificationUiHandlers;
import stroom.streamstore.client.presenter.ClassificationWrapperPresenter.ClassificationWrapperView;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tab.client.presenter.ImageIcon;

public class SteppingContentTabPresenter extends ContentTabPresenter<ClassificationWrapperView>
        implements HasDirtyHandlers, CloseHandler, ClassificationUiHandlers {
    private final SteppingPresenter steppingPresenter;
    private DocRef pipeline;
    private boolean dirty;
    private boolean reading;
    private String lastLabel;

    @Inject
    public SteppingContentTabPresenter(final EventBus eventBus, final ClassificationWrapperView view,
                                       final SteppingPresenter steppingPresenter) {
        super(eventBus, view);
        this.steppingPresenter = steppingPresenter;

        steppingPresenter.setClassificationUiHandlers(this);
        setInSlot(ClassificationWrapperView.CONTENT, steppingPresenter);
    }

    @Override
    public void setClassification(final String classification) {
        getView().setClassification(classification);
    }

    @Override
    protected void onBind() {
        registerHandler(steppingPresenter.addDirtyHandler(new DirtyHandler() {
            @Override
            public void onDirty(final DirtyEvent event) {
                setDirty(event.isDirty());
            }
        }));
    }

    @Override
    public void onCloseRequest(final CloseCallback callback) {
        if (dirty) {
            ConfirmEvent.fire(this,
                    pipeline.getType() + " '" + pipeline.getName()
                            + "' has unsaved changes. Are you sure you want to close this item?",
                    new ConfirmCallback() {
                        @Override
                        public void onResult(final boolean result) {
                            callback.closeTab(result);
                            if (result) {
                                unbind();
                            }
                        }
                    });
        } else {
            callback.closeTab(true);
            unbind();
        }
    }

    public void read(final DocRef pipeline, final Stream stream, final long eventId,
                     final StreamType childStreamType) {
        reading = true;
        this.pipeline = pipeline;
        steppingPresenter.read(pipeline, stream, eventId, childStreamType);
        reading = false;
    }

    private void onDirty(final boolean dirty) {
        // Only fire tab refresh if the tab has changed.
        if (lastLabel == null || !lastLabel.equals(getLabel())) {
            lastLabel = getLabel();
            RefreshContentTabEvent.fire(this, this);
        }
    }

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
    public Icon getIcon() {
        return ImageIcon.create(TAB_IMAGE_URL + "stepping.png");
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
}
