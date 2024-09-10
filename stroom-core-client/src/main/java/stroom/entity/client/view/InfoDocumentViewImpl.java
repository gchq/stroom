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

package stroom.entity.client.view;

import stroom.entity.client.presenter.InfoDocumentPresenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class InfoDocumentViewImpl extends ViewImpl implements InfoDocumentPresenter.InfoDocumentView {

    private final HTML textArea;
    private final SimplePanel layout;

    @Inject
    public InfoDocumentViewImpl() {
        textArea = new HTML();
        textArea.setStyleName("info-layout");

        layout = new SimplePanel();
        layout.setWidget(textArea);
        layout.setStyleName("max");
    }

    @Override
    public Widget asWidget() {
        return layout;
    }

    @Override
    public void focus() {
//        textArea.setFocus(true);
    }

    @Override
    public void setInfo(final SafeHtml html) {
        textArea.setHTML(html);
    }
}
