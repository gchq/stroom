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

public class LinkTabBar extends AbstractTabBar {

    public LinkTabBar() {
        final Element element = DOM.createDiv();
        element.setClassName("linkTabBar");

        setElement(element);
    }

    @Override
    protected AbstractTab createTab(final TabData tabData) {
        return new LinkTab(tabData.getLabel());
    }

    @Override
    protected AbstractTabSelector createTabSelector() {
        return new BasicTabSelector();
    }

    @Override
    protected Element createSeparator() {
        final Element separator = DOM.createDiv();
        separator.setClassName("linkTabBar-separator");
        separator.setInnerText("|");
        return separator;
    }
}
