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

package stroom.data.store.impl.fs.client.view;

import stroom.data.store.impl.fs.client.presenter.FsVolumeGroupEditPresenter.FsVolumeGroupEditView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class FsVolumeGroupEditViewImpl extends ViewImpl implements FsVolumeGroupEditView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    SimplePanel list;

    @Inject
    public FsVolumeGroupEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public String getName() {
        return this.name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setListView(final View listView) {
        list.setWidget(listView.asWidget());
    }

    public interface Binder extends UiBinder<Widget, FsVolumeGroupEditViewImpl> {

    }
}
