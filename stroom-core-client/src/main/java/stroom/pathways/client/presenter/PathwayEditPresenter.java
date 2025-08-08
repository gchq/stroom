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

package stroom.pathways.client.presenter;

import stroom.pathways.client.presenter.PathwayEditPresenter.PathwayEditView;
import stroom.pathways.shared.pathway.Pathway;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import javax.validation.ValidationException;

public class PathwayEditPresenter extends MyPresenterWidget<PathwayEditView> {

    private Pathway pathway;

    @Inject
    public PathwayEditPresenter(final EventBus eventBus, final PathwayEditView view) {
        super(eventBus, view);
    }

    public void read(final Pathway pathway) {
        this.pathway = pathway;
        getView().setName(pathway.getName());
    }

    public Pathway write() {
        String name = getView().getName();
        name = name.trim();

        if (name.isEmpty()) {
            throw new ValidationException("A pathway must have a name");
        }

        return new Pathway(name, pathway.getPathKey(), pathway.getRoot());
    }

    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizable(300, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    public interface PathwayEditView extends View, Focus {

        String getName();

        void setName(final String name);
    }
}
