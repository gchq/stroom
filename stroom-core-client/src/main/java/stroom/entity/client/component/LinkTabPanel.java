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

package stroom.entity.client.component;

import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.AriaPanel;
import stroom.widget.component.client.Component;
import stroom.widget.component.client.LayerContainer;
import stroom.widget.tab.client.LayerContainerImpl;
import stroom.widget.tab.client.LinkTabBar;
import stroom.widget.tab.client.TabBar;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// A row of link tabs, then a row of toolbars, and the tab showing: what a document's editor, or a
/// folder's tabs, are shown in (see [TabbedController]). At the far right of the tab row a status
/// says when what is shown is read only, and why.
public class LinkTabPanel extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final Template TEMPLATE = GWT.create(Template.class);
    // Why something shown is read only: it is only ever read only for a user who can't change it
    private static final String READ_ONLY_REASON = "You don't have permission to change this";
    private static final SafeHtml READ_ONLY_CHIP = TEMPLATE.readOnlyChip(
            SafeHtmlUtil.getSafeHtmlFromSafeConstant(SvgImage.LOCKED.getSvg()), ". " + READ_ONLY_REASON + ".");

    @UiField
    LinkTabBar tabBar;
    @UiField
    AriaPanel status;
    @UiField
    FlowPanel toolbarContainer;
    @UiField
    Toolbar toolbar;
    @UiField
    LayerContainerImpl layerContainer;

    /// Makes an empty panel.
    public LinkTabPanel() {
        initWidget(BINDER.createAndBindUi(this));
        // The tabs name the panel their content is shown in
        tabBar.setTabPanel(layerContainer.getElement());
    }

    /// @return The tab bar.
    public TabBar getTabBar() {
        return tabBar;
    }

    /// @return Where the tab showing is shown.
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    /// @return The toolbar at the start of the toolbar row, hidden until it is shown
    /// ([#setToolbarVisible(boolean)]).
    public Toolbar getToolbar() {
        return toolbar;
    }

    /// @param visible Whether to show the toolbar, e.g. once it has buttons.
    public void setToolbarVisible(final boolean visible) {
        toolbar.setVisible(visible);
    }

    /// Shows the tab showing's own toolbars after the toolbar, in place of any shown.
    ///
    /// @param toolbars The toolbars.
    public void setTabToolbars(final Iterable<Widget> toolbars) {
        while (toolbarContainer.getWidgetCount() > 1) {
            toolbarContainer.remove(1);
        }
        for (final Widget widget : toolbars) {
            toolbarContainer.add(widget);
        }
    }

    /// Says, or stops saying, that what is shown is read only, and why. It is put in and taken out
    /// of the status, not shown and hidden, so that screen readers announce it.
    ///
    /// @param readOnly Whether to say it.
    public void setReadOnlyStatus(final boolean readOnly) {
        if (readOnly) {
            status.getElement().setInnerSafeHtml(READ_ONLY_CHIP);
            status.setTitle(READ_ONLY_REASON);
        } else {
            status.getElement().setInnerSafeHtml(SafeHtmlUtils.EMPTY_SAFE_HTML);
            status.setTitle(null);
        }
        // The status takes room from the tab bar, so it lays its tabs out again
        tabBar.refresh();
    }

    interface Binder extends UiBinder<Widget, LinkTabPanel> {

    }

    // "Read only" with a lock, and the reason for screen readers, which a tooltip doesn't give them
    interface Template extends SafeHtmlTemplates {

        @Template("<span class=\"docTab-readOnlyNote\"><span class=\"docTab-readOnlyIcon\" aria-hidden=\"true\">"
                  + "{0}</span>Read only</span><span class=\"sr-only\">{1}</span>")
        SafeHtml readOnlyChip(SafeHtml lock, String reason);
    }
}
