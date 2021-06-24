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

package stroom.widget.tab.client.view;

import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.DOM;

public class CurveTabBar extends AbstractTabBar {

    private final Element element;

    public CurveTabBar() {
        element = DOM.createDiv();
        element.setClassName("curveTabBar");

        setElement(element);

        ResizeObserver.observe(element, (e) -> onResize());
    }

    @Override
    protected AbstractTab createTab(final TabData tabData) {
        return new CurveTab(tabData.getIcon(), tabData.getLabel(), tabData.isCloseable());
    }

    @Override
    protected AbstractTabSelector createTabSelector() {
        return new CurveTabSelector();
    }

    @Override
    protected int getTabGap() {
        return -10;
    }
}
