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

import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.widget.iframe.client.presenter.IFramePresenter.IFrameView;

public class IFrameViewImpl extends ViewImpl implements IFrameView {
    private final Frame frame;

    @Inject
    public IFrameViewImpl() {
        frame = new Frame();
        final com.google.gwt.dom.client.Style style = frame.getElement().getStyle();
        style.setWidth(100, Unit.PCT);
        style.setHeight(100, Unit.PCT);
        style.setBorderWidth(1, Unit.PX);
        style.setBorderStyle(BorderStyle.SOLID);
        style.setBorderColor("#C5CDE2");
    }

    @Override
    public Widget asWidget() {
        return frame;
    }

    @Override
    public void setUrl(final String url) {
        frame.setUrl(url);
    }
}
