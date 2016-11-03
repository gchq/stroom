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

package stroom.pool.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.pool.client.presenter.PoolPresenter;
import stroom.pool.client.presenter.PoolPresenter.PoolView;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class PoolViewImpl extends ViewImpl implements PoolView {
    public interface Binder extends UiBinder<Widget, PoolViewImpl> {
    }

    private final Widget widget;

    @UiField
    ResizeSimplePanel poolList;
    @UiField
    ResizeSimplePanel poolNodeList;

    @Inject
    public PoolViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (PoolPresenter.LIST.equals(slot)) {
            poolList.setWidget(content);
        } else if (PoolPresenter.NODE_LIST.equals(slot)) {
            poolNodeList.setWidget(content);
        }
    }
}
