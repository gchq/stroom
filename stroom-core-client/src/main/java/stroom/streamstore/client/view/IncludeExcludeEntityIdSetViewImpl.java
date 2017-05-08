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
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetPresenter.IncludeExcludeEntityIdSetView;
import stroom.streamstore.client.presenter.IncludeExcludeEntityIdSetUiHandlers;
import stroom.widget.button.client.GlyphButton;
import stroom.widget.button.client.GlyphIcons;

public class IncludeExcludeEntityIdSetViewImpl extends ViewWithUiHandlers<IncludeExcludeEntityIdSetUiHandlers>
        implements IncludeExcludeEntityIdSetView {
    public interface Binder extends UiBinder<Widget, IncludeExcludeEntityIdSetViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    GlyphButton edit;
    @UiField
    ScrollPanel list;

    @Inject
    public IncludeExcludeEntityIdSetViewImpl(final Binder binder) {
        edit = GlyphButton.create(GlyphIcons.EDIT);
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
    public void setEditEnabled(final boolean enabled) {
        edit.setEnabled(enabled);
    }

    @UiHandler("edit")
    public void onEditClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onEdit();
        }
    }
}
