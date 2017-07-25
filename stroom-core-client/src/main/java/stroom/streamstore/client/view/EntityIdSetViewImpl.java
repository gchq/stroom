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

package stroom.streamstore.client.view;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.streamstore.client.presenter.EntityIdSetPresenter.EntityIdSetView;
import stroom.streamstore.client.presenter.EntityIdSetUiHandlers;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;

public class EntityIdSetViewImpl extends ViewWithUiHandlers<EntityIdSetUiHandlers>implements EntityIdSetView {
    public interface Binder extends UiBinder<Widget, EntityIdSetViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    SvgButton add;
    @UiField(provided = true)
    SvgButton remove;
    @UiField
    ScrollPanel list;

    @Inject
    public EntityIdSetViewImpl(final Binder binder) {
        add = SvgButton.create(SvgPresets.ADD);
        remove = SvgButton.create(SvgPresets.REMOVE);
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setListView(final View view) {
        this.list.setWidget(view.asWidget());
    }

    @Override
    public void setAddEnabled(final boolean enabled) {
        add.setEnabled(enabled);
    }

    @Override
    public void setRemoveEnabled(final boolean enabled) {
        remove.setEnabled(enabled);
    }

    @UiHandler("add")
    public void onAddClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onAdd();
        }
    }

    @UiHandler("remove")
    public void onRemoveClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRemove();
        }
    }
}
