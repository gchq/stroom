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

package stroom.query.client.view;

import stroom.query.client.presenter.ResultStoreSettingsPresenter.ResultStoreSettingsView;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ResultStoreSettingsViewImpl extends ViewImpl implements ResultStoreSettingsView {

    private final Widget widget;

    @UiField
    TextBox searchProcessTimeToIdle;
    @UiField
    TextBox searchProcessTimeToLive;
    @UiField
    CustomCheckBox searchProcessDestroyOnTabClose;
    @UiField
    CustomCheckBox searchProcessDestroyOnWindowClose;

    @UiField
    TextBox storeTimeToIdle;
    @UiField
    TextBox storeTimeToLive;
    @UiField
    CustomCheckBox storeDestroyOnTabClose;
    @UiField
    CustomCheckBox storeDestroyOnWindowClose;

    @Inject
    public ResultStoreSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        searchProcessTimeToIdle.setFocus(true);
    }

    @Override
    public String getSearchProcessTimeToIdle() {
        return searchProcessTimeToIdle.getText();
    }

    @Override
    public void setSearchProcessTimeToIdle(final String searchProcessTimeToIdle) {
        this.searchProcessTimeToIdle.setValue(searchProcessTimeToIdle);
    }

    @Override
    public String getSearchProcessTimeToLive() {
        return searchProcessTimeToLive.getText();
    }

    @Override
    public void setSearchProcessTimeToLive(final String searchProcessTimeToLive) {
        this.searchProcessTimeToLive.setValue(searchProcessTimeToLive);
    }

    @Override
    public boolean isSearchProcessDestroyOnTabClose() {
        return searchProcessDestroyOnTabClose.getValue();
    }

    @Override
    public void setSearchProcessDestroyOnTabClose(final boolean searchProcessDestroyOnTabClose) {
        this.searchProcessDestroyOnTabClose.setValue(searchProcessDestroyOnTabClose);
    }

    @Override
    public boolean isSearchProcessDestroyOnWindowClose() {
        return searchProcessDestroyOnWindowClose.getValue();
    }

    @Override
    public void setSearchProcessDestroyOnWindowClose(final boolean searchProcessDestroyOnWindowClose) {
        this.searchProcessDestroyOnWindowClose.setValue(searchProcessDestroyOnWindowClose);
    }

    @Override
    public String getStoreTimeToIdle() {
        return storeTimeToIdle.getText();
    }

    @Override
    public void setStoreTimeToIdle(final String storeTimeToIdle) {
        this.storeTimeToIdle.setValue(storeTimeToIdle);
    }

    @Override
    public String getStoreTimeToLive() {
        return storeTimeToLive.getText();
    }

    @Override
    public void setStoreTimeToLive(final String storeTimeToLive) {
        this.storeTimeToLive.setValue(storeTimeToLive);
    }

    @Override
    public boolean isStoreDestroyOnTabClose() {
        return storeDestroyOnTabClose.getValue();
    }

    @Override
    public void setStoreDestroyOnTabClose(final boolean storeDestroyOnTabClose) {
        this.storeDestroyOnTabClose.setValue(storeDestroyOnTabClose);
    }

    @Override
    public boolean isStoreDestroyOnWindowClose() {
        return storeDestroyOnWindowClose.getValue();
    }

    @Override
    public void setStoreDestroyOnWindowClose(final boolean storeDestroyOnWindowClose) {
        this.storeDestroyOnWindowClose.setValue(storeDestroyOnWindowClose);
    }

    public interface Binder extends UiBinder<Widget, ResultStoreSettingsViewImpl> {

    }
}
