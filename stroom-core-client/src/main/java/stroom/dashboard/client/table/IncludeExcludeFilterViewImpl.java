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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.IncludeExcludeFilterPresenter.IncludeExcludeFilterView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class IncludeExcludeFilterViewImpl extends ViewImpl implements IncludeExcludeFilterView {

    private final Widget widget;
    @UiField
    TextArea includeText;
    @UiField
    TextArea excludeText;
    @UiField
    SimplePanel includeDictionaryPanel;
    @UiField
    SimplePanel excludeDictionaryPanel;

    @Inject
    public IncludeExcludeFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setIncludeDictionaryPanel(final View view) {
        includeDictionaryPanel.setWidget(view.asWidget());
    }

    @Override
    public void setExcludeDictionaryPanel(final View view) {
        excludeDictionaryPanel.setWidget(view.asWidget());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        includeText.setFocus(true);
    }

    @Override
    public String getIncludes() {
        return this.includeText.getText();
    }

    @Override
    public void setIncludes(final String includes) {
        this.includeText.setText(includes);
    }

    @Override
    public String getExcludes() {
        return this.excludeText.getText();
    }

    @Override
    public void setExcludes(final String excludes) {
        this.excludeText.setText(excludes);
    }

    public interface Binder extends UiBinder<Widget, IncludeExcludeFilterViewImpl> {

    }
}
