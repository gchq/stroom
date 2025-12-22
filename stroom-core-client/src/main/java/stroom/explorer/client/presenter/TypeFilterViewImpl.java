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

package stroom.explorer.client.presenter;

import stroom.explorer.client.presenter.TypeFilterPresenter.TypeFilterView;

import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class TypeFilterViewImpl extends ViewImpl implements TypeFilterView {
    private final ScrollPanel scrollPanel;

    public TypeFilterViewImpl() {
        scrollPanel = new ScrollPanel();
        scrollPanel.getElement().getStyle().setProperty("minWidth", 50 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxWidth", 600 + "px");
        scrollPanel.getElement().getStyle().setProperty("maxHeight", 600 + "px");
    }

    @Override
    public void setWidget(final Widget widget) {
        scrollPanel.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return scrollPanel;
    }
}
