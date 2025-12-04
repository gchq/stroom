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

package stroom.importexport.client.presenter;

import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import javax.inject.Inject;

public class DependenciesInfoViewImpl extends ViewImpl implements DependenciesInfoPresenter.DependenciesInfoView {

    private final TextArea widget;

    @Inject
    public DependenciesInfoViewImpl() {
        widget = new TextArea();
        widget.setReadOnly(true);
        widget.setStyleName("info-layout");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInfo(final String string) {
        widget.setText(string);
    }
}
