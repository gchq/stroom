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

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;
import stroom.widget.tab.client.presenter.TabData;

public class CurveTabBar extends AbstractTabBar {
    public interface Style extends CssResource {
        String curveTabBar();
    }

    public interface Resources extends ClientBundle {
        @Source("CurveTabBar.css")
        Style style();
    }

    private static Resources resources;

    private final Element element;

    public CurveTabBar() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        element = DOM.createDiv();
        element.setClassName(resources.style().curveTabBar());

        setElement(element);
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
