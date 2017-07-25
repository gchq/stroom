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

package stroom.widget.tooltip.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.widget.tooltip.client.presenter.TooltipPresenter.TooltipView;

public class TooltipViewImpl extends ViewImpl implements TooltipView {
    private static Resources resources;
    private final HTML content;

    public TooltipViewImpl() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        content = new HTML();
        content.setStyleName(resources.style().tooltip());
    }

    @Override
    public Widget asWidget() {
        return content;
    }

    @Override
    public void setHTML(final String html) {
        content.setHTML(html);
    }

    @Override
    public void setText(final String text) {
        content.setText(text);
    }

    public interface Style extends CssResource {
        String tooltip();
    }

    public interface Resources extends ClientBundle {
        @Source("Tooltip.css")
        Style style();
    }
}
