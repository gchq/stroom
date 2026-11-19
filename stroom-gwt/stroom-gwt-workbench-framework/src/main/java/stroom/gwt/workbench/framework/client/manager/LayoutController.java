/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;

/// Controls the manager's layout: whether the sidebar, toolbar and addon panel are shown, where
/// the panel is, full screen mode, and the sizes set by dragging the resize handles. The limits
/// match React Storybook's.
public class LayoutController {

    private static final String LAYOUT_ID = "wbm-layout";
    private static final String SIDEBAR_RESIZER_ID = "wbm-sidebar-resizer";
    private static final String PANEL_RESIZER_ID = "wbm-panel-resizer";

    private static final String CLASS_PANEL_RIGHT = "wbm-layout--panel-right";
    private static final String CLASS_PANEL_HIDDEN = "wbm-layout--panel-hidden";
    private static final String CLASS_SIDEBAR_HIDDEN = "wbm-layout--sidebar-hidden";
    private static final String CLASS_TOOLBAR_HIDDEN = "wbm-layout--toolbar-hidden";
    private static final String CLASS_FULLSCREEN = "wbm-layout--fullscreen";
    private static final String CLASS_SETTINGS = "wbm-layout--settings";
    private static final String CLASS_DRAGGING = "wbm-layout--dragging";

    private static final int DEFAULT_SIDEBAR_WIDTH = 300;
    private static final int MIN_SIDEBAR_WIDTH = 230;
    private static final int DEFAULT_PANEL_HEIGHT = 300;
    private static final int DEFAULT_PANEL_WIDTH = 400;
    // The panel can be shrunk to just its tab bar
    private static final int MIN_PANEL_SIZE = 40;
    private static final int MIN_CONTENT_SIZE = 40;

    private final Element layout;
    private boolean sidebarVisible = true;
    private boolean toolbarVisible = true;
    private boolean panelVisible = true;
    private boolean panelRight;
    private boolean fullscreen;
    private int sidebarWidth = DEFAULT_SIDEBAR_WIDTH;
    private int panelHeight = DEFAULT_PANEL_HEIGHT;
    private int panelWidth = DEFAULT_PANEL_WIDTH;
    private Runnable changeHandler = () -> {
    };
    private HandlerRegistration dragRegistration;

    /// Finds the layout's elements, binds the resize handles and applies the default layout.
    public LayoutController() {
        layout = Document.get().getElementById(LAYOUT_ID);
        bindResizer(Document.get().getElementById(SIDEBAR_RESIZER_ID), true);
        bindResizer(Document.get().getElementById(PANEL_RESIZER_ID), false);
        // The mouse up may never arrive, e.g. if released outside the window
        BrowserUtil.addWindowBlurHandler(this::endDrag);
        apply();
    }

    /// @param changeHandler Called whenever the layout changes, e.g. to update menus.
    public void setChangeHandler(final Runnable changeHandler) {
        this.changeHandler = changeHandler;
    }

    /// @return True if the sidebar is shown.
    public boolean isSidebarVisible() {
        return sidebarVisible && !fullscreen;
    }

    /// @return True if the toolbar is shown.
    public boolean isToolbarVisible() {
        return toolbarVisible;
    }

    /// @return True if the addon panel is shown.
    public boolean isPanelVisible() {
        return panelVisible && !fullscreen;
    }

    /// @return True if the addon panel is to the right of the preview rather than below it.
    public boolean isPanelRight() {
        return panelRight;
    }

    /// @return True if in full screen mode, i.e. without the sidebar and panel.
    public boolean isFullscreen() {
        return fullscreen;
    }

    /// Shows or hides the sidebar.
    public void toggleSidebar() {
        if (fullscreen) {
            fullscreen = false;
            sidebarVisible = true;
        } else {
            sidebarVisible = !sidebarVisible;
        }
        apply();
    }

    /// Shows or hides the toolbar.
    public void toggleToolbar() {
        toolbarVisible = !toolbarVisible;
        apply();
    }

    /// Shows or hides the addon panel.
    public void togglePanel() {
        if (fullscreen) {
            fullscreen = false;
            panelVisible = true;
        } else {
            panelVisible = !panelVisible;
        }
        apply();
    }

    /// Hides the addon panel.
    public void hidePanel() {
        panelVisible = false;
        apply();
    }

    /// Moves the addon panel between the bottom and the right.
    public void togglePanelPosition() {
        panelRight = !panelRight;
        apply();
    }

    /// Turns full screen mode on or off.
    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        apply();
    }

    /// Shows or hides the settings pages (about/shortcuts) in place of the preview and panel.
    ///
    /// @param settings True to show the settings pages.
    public void setSettingsVisible(final boolean settings) {
        setClass(CLASS_SETTINGS, settings);
    }

    private void apply() {
        setClass(CLASS_SIDEBAR_HIDDEN, !sidebarVisible);
        setClass(CLASS_TOOLBAR_HIDDEN, !toolbarVisible);
        setClass(CLASS_PANEL_HIDDEN, !panelVisible);
        setClass(CLASS_PANEL_RIGHT, panelRight);
        setClass(CLASS_FULLSCREEN, fullscreen);
        BrowserUtil.setCssVariable(layout, "--wbm-sidebar-width", sidebarWidth + "px");
        BrowserUtil.setCssVariable(layout, "--wbm-panel-size", panelHeight + "px");
        BrowserUtil.setCssVariable(layout, "--wbm-panel-width", panelWidth + "px");
        changeHandler.run();
    }

    private void setClass(final String className, final boolean set) {
        if (set) {
            layout.addClassName(className);
        } else {
            layout.removeClassName(className);
        }
    }

    private void bindResizer(final Element handle, final boolean sidebar) {
        BrowserUtil.addListener(handle, "mousedown", event -> {
            if (event.getButton() != Event.BUTTON_LEFT) {
                return;
            }
            event.preventDefault();
            // Only one drag at a time, in case the last one never saw its mouse up
            endDrag();
            // Stop the preview iframe swallowing mouse events while dragging
            setClass(CLASS_DRAGGING, true);
            dragRegistration = Event.addNativePreviewHandler(preview -> {
                final int type = preview.getTypeInt();
                if (type == Event.ONMOUSEMOVE && BrowserUtil.getButtons(preview.getNativeEvent()) == 0) {
                    // The button was released somewhere the mouse up wasn't seen
                    endDrag();
                } else if (type == Event.ONMOUSEMOVE) {
                    preview.getNativeEvent().preventDefault();
                    if (sidebar) {
                        resizeSidebar(preview.getNativeEvent().getClientX());
                    } else {
                        resizePanel(preview.getNativeEvent().getClientX(),
                                preview.getNativeEvent().getClientY());
                    }
                } else if (type == Event.ONMOUSEUP) {
                    endDrag();
                }
            });
        });
    }

    private void endDrag() {
        setClass(CLASS_DRAGGING, false);
        if (dragRegistration != null) {
            dragRegistration.removeHandler();
            dragRegistration = null;
        }
    }

    private void resizeSidebar(final int clientX) {
        final int max = Window.getClientWidth() - MIN_CONTENT_SIZE;
        sidebarWidth = clamp(clientX, MIN_SIDEBAR_WIDTH, max);
        apply();
    }

    private void resizePanel(final int clientX, final int clientY) {
        if (panelRight) {
            final int left = isSidebarVisible()
                    ? sidebarWidth
                    : 0;
            final int max = Window.getClientWidth() - left - MIN_CONTENT_SIZE;
            panelWidth = clamp(Window.getClientWidth() - clientX, MIN_PANEL_SIZE, max);
        } else {
            final int max = Window.getClientHeight() - MIN_CONTENT_SIZE;
            panelHeight = clamp(Window.getClientHeight() - clientY, MIN_PANEL_SIZE, max);
        }
        apply();
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
    }
}
