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

package stroom.widget.iframe.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.cell.clickable.client.Hyperlink;
import stroom.widget.iframe.client.presenter.IFramePresenter.IFrameView;

public class IFrameViewImpl extends ViewImpl implements IFrameView {
    private static Resources resources;
    private final Frame content;

    public IFrameViewImpl() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        content = new Frame();
        content.setStyleName(resources.style().iframe());
    }

    @Override
    public Widget asWidget() {
        return content;
    }

    @Override
    public void setHyperlink(final Hyperlink hyperlink) {
        this.content.setUrl(hyperlink.getHref());
    }

    public interface Style extends CssResource {
        String iframe();
    }

    public interface Resources extends ClientBundle {
        @Source("IFrame.css")
        Style style();
    }
}
