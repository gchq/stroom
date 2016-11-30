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

package stroom.app.client.view;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.app.client.presenter.AppPresenter;

public class AppViewImpl extends ViewImpl implements AppPresenter.AppView {
    private final Element loadingText;
    private final Element loading;

    public AppViewImpl() {
        loadingText = RootPanel.get("loadingText").getElement();
        loading = RootPanel.get("loading").getElement();
    }

    @Override
    public Widget asWidget() {
        return null;
    }

    @Override
    public void showWorking(final String message) {
        loading.getStyle().setOpacity(1);

        if (message != null) {
            loadingText.setInnerText(message);
        }
    }

    @Override
    public void hideWorking() {
        loading.getStyle().setOpacity(0);
    }
}
