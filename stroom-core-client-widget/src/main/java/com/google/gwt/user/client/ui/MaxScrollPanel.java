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

package com.google.gwt.user.client.ui;

import com.google.gwt.dom.client.Style.Unit;

public class MaxScrollPanel extends ScrollPanel {
    public MaxScrollPanel() {
        setSize("100%", "100%");
        getContainerElement().getStyle().setWidth(100, Unit.PCT);
        getContainerElement().getStyle().setHeight(100, Unit.PCT);
    }

    public MaxScrollPanel(final Widget widget) {
        this();
        setWidget(widget);
    }
}
