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

package stroom.widget.tab.client;


import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiTemplate;
import com.google.gwt.user.client.DOM;

/// The link tabs' bar, its tabs separated by "|". Its markup is `LinkTabBar.ui.xml`.
public class LinkTabBar extends AbstractTabBar {

    private static final Binder BINDER = GWT.create(Binder.class);

    public LinkTabBar() {
        this(new Parts());
    }

    private LinkTabBar(final Parts parts) {
        super(BINDER.createAndBindUi(parts), parts.tabList);
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        GlobalResizeObserver.addListener(getElement(), e -> onResize());
    }

    @Override
    protected void onDetach() {
        GlobalResizeObserver.removeListener(getElement());
        super.onDetach();
    }

    @Override
    protected AbstractTab createTab(final TabData tabData) {
        return new LinkTab(
                tabData.getLabel(),
                tabData.getTooltip().orElse(null));
    }

    @Override
    protected Element createSeparator() {
        final Element separator = DOM.createDiv();
        separator.setClassName("tab-bar__separator");
        separator.setInnerText("|");
        return separator;
    }


    // --------------------------------------------------------------------------------


    // The parts of the template the bar needs; the bar's element itself is the template's root
    static final class Parts {

        @UiField
        DivElement tabList;
    }


    // --------------------------------------------------------------------------------


    @UiTemplate("LinkTabBar.ui.xml")
    interface Binder extends UiBinder<DivElement, Parts> {

    }
}
