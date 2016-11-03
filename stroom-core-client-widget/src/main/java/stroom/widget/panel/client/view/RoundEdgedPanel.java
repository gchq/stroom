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

package stroom.widget.panel.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class RoundEdgedPanel extends SimplePanel {
    interface Binder extends UiBinder<Widget, RoundEdgedPanel> {
    }

    private static Binder binder = GWT.create(Binder.class);

    @UiField
    FlowPanel content;

    public RoundEdgedPanel() {
        super.setWidget(binder.createAndBindUi(this));
    }

    @Override
    public void setWidget(Widget w) {
        content.clear();
        content.add(w);
    }

    @Override
    public void add(Widget w) {
        content.add(w);
    }
}
