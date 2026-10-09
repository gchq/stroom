/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.client.view;

import stroom.sqlstore.client.presenter.SqlTemporalStoreSettingsPresenter.SqlTemporalStoreSettingsView;
import stroom.sqlstore.client.presenter.SqlTemporalStoreSettingsUiHandlers;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class SqlTemporalStoreSettingsViewImpl
        extends ViewWithUiHandlers<SqlTemporalStoreSettingsUiHandlers>
        implements SqlTemporalStoreSettingsView {

    private final Widget widget;

    @UiField
    Label count;
    @UiField
    Button reset;

    @Inject
    public SqlTemporalStoreSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setCount(final long count) {
        this.count.setText(String.valueOf(count));
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        reset.setEnabled(!readOnly);
    }

    @UiHandler("reset")
    @SuppressWarnings("unused")
    public void onReset(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onReset();
        }
    }

    public interface Binder extends UiBinder<Widget, SqlTemporalStoreSettingsViewImpl> {

    }
}
