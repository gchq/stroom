/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.widget.component.client.Component;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.LayerContainerImpl;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabData;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// A StroomQL query: its help beside the editor (both shown here by its controller), above the
/// results, one tab each for the table and the visualisation.
public class QueryEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel help;
    @UiField
    CodeEditor editor;
    @UiField
    LinkTabBar tabBar;
    @UiField
    LayerContainerImpl layerContainer;

    /// Makes it, with no tabs.
    public QueryEdit() {
        initWidget(BINDER.createAndBindUi(this));
        tabBar.setTabPanel(layerContainer.getElement());
    }

    /// @param help The query's help.
    public void setHelp(final IsWidget help) {
        this.help.setWidget(help);
    }

    /// @return The query's editor, for its controller to attach to.
    public CodeEditor getEditor() {
        return editor;
    }

    /// @param tab A results tab to add.
    public void addTab(final TabData tab) {
        tabBar.addTab(tab);
    }

    /// @param tab    A results tab.
    /// @param hidden Whether it is hidden.
    public void setTabHidden(final TabData tab, final boolean hidden) {
        tabBar.setTabHidden(tab, hidden);
    }

    /// @param tab A results tab.
    /// @return Whether it is hidden.
    public boolean isTabHidden(final TabData tab) {
        return tabBar.isTabHidden(tab);
    }

    /// @return The results tab selected.
    public TabData getSelectedTab() {
        return tabBar.getSelectedTab();
    }

    /// Selects a results tab, showing its content.
    ///
    /// @param tab     The tab.
    /// @param content What it shows.
    public void showTab(final TabData tab, final Layer content) {
        tabBar.selectTab(tab);
        layerContainer.show(content);
    }

    /// Shows a selected tab's new content.
    ///
    /// @param content What it shows.
    public void showContent(final Layer content) {
        layerContainer.show(content);
    }

    /// @param onSelectTab Given the results tab the user selects.
    public void setOnSelectTab(final Consumer<TabData> onSelectTab) {
        tabBar.setOnSelect(onSelectTab);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryEdit> {

    }
}
