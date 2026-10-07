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
import stroom.gwt.workbench.framework.client.shortcuts.KeyCombo;
import stroom.gwt.workbench.framework.client.story.StoryTheme;
import stroom.gwt.workbench.framework.client.story.StoryUrls;
import stroom.gwt.workbench.framework.client.tools.ViewportPreset;
import stroom.gwt.workbench.framework.client.tools.VisionFilter;
import stroom.gwt.workbench.framework.client.tools.ZoomLevels;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// The preview tools in the toolbar, as in React Storybook: measure, outline, viewport size,
/// vision filter and zoom, and the theme. The measure, outline and vision tools work inside the
/// preview, so their state is sent to it each time it loads. The theme is given in the preview's
/// URL (see [StoryUrls#previewUrl(String, String, StoryTheme)]), as stories need it before they
/// render, so choosing one loads the story again; it is remembered in the browser's local storage.
public class PreviewTools {

    private static final String PREVIEW_ID = "workbench-preview-wrapper";
    private static final String FRAME_ID = "wbm-viewport-frame";
    private static final String ZOOM_WRAPPER_ID = "wbm-zoom-wrapper";
    private static final String CLASS_VIEWPORT = "wbm-preview--viewport";
    private static final String CLASS_ACTIVE = "wbm-icon-button--active";
    private static final String CLASS_DRAGGING = "wbm-preview--dragging";

    private static final String RESET = "reset";
    private static final String THEME_STORAGE_KEY = "wbm-theme";
    private static final String ZOOM_IN = "zoom-in";
    private static final String ZOOM_OUT = "zoom-out";
    private static final String ZOOM_PREFIX = "zoom-";
    private static final int[] ZOOM_PRESETS = {50, 100, 200};
    private static final int MIN_VIEWPORT_SIZE = 40;

    private final IFrameElement iframe;
    private final ShortcutHandler shortcutHandler;
    private final Element preview;
    private final Element frame;
    private final Element zoomWrapper;
    private final InputElement widthInput;
    private final InputElement heightInput;

    private StoryTheme theme = StoryTheme.fromId(BrowserUtil.getLocalStorage(THEME_STORAGE_KEY));
    private Runnable themeChangeHandler = () -> {
    };
    private boolean outline;
    private boolean measure;
    private VisionFilter vision;
    private int zoom = ZoomLevels.DEFAULT;
    // The selected viewport size, or null to fill the preview
    private ViewportPreset viewport;
    private int viewportWidth;
    private int viewportHeight;
    private boolean customViewport;
    private HandlerRegistration dragRegistration;

    /// @param iframe          The preview iframe.
    /// @param shortcutHandler For the zoom tool's keyboard shortcuts.
    public PreviewTools(final IFrameElement iframe, final ShortcutHandler shortcutHandler) {
        this.iframe = iframe;
        this.shortcutHandler = shortcutHandler;
        final Document document = Document.get();
        preview = document.getElementById(PREVIEW_ID);
        frame = document.getElementById(FRAME_ID);
        zoomWrapper = document.getElementById(ZOOM_WRAPPER_ID);
        widthInput = document.getElementById("wbm-viewport-width").cast();
        heightInput = document.getElementById("wbm-viewport-height").cast();
    }

    /// Wires up the toolbar buttons and the zoom shortcuts.
    public void bind() {
        onClick("wbm-outline", () -> {
            outline = !outline;
            setActive("wbm-outline", outline);
            sendStateToPreview();
        });
        onClick("wbm-measure", () -> {
            measure = !measure;
            setActive("wbm-measure", measure);
            sendStateToPreview();
        });
        onClick("wbm-viewport-button", this::toggleViewportMenu);
        onClick("wbm-vision-button", this::toggleVisionMenu);
        onClick("wbm-theme-button", this::toggleThemeMenu);
        showTheme();
        onClick("wbm-zoom", this::toggleZoomMenu);
        onClick("wbm-viewport-rotate", () -> setViewportSize(viewportHeight, viewportWidth, customViewport));
        bindSizeInput(widthInput, true);
        bindSizeInput(heightInput, false);
        bindViewportHandles();
        // The mouse up may never arrive, e.g. if released outside the window
        BrowserUtil.addWindowBlurHandler(this::endDrag);

        shortcutHandler.registerFixed(KeyCombo.parse("alt+="), "Zoom in", () -> setZoom(ZoomLevels.zoomIn(zoom)));
        shortcutHandler.registerFixed(KeyCombo.parse("alt+-"), "Zoom out", () -> setZoom(ZoomLevels.zoomOut(zoom)));
        shortcutHandler.registerFixed(KeyCombo.parse("alt+0"), "Reset zoom",
                () -> setZoom(ZoomLevels.DEFAULT));

        // The preview forgets the tools' state each time it loads a story
        BrowserUtil.addListener(iframe, "load", event -> sendStateToPreview());
        applyZoom();
        applyViewport();
    }

    /// @return The theme chosen for the stories.
    public StoryTheme getTheme() {
        return theme;
    }

    /// @param themeChangeHandler What to do when a different theme is chosen, e.g. load the story
    ///                           again in it.
    public void setThemeChangeHandler(final Runnable themeChangeHandler) {
        this.themeChangeHandler = Objects.requireNonNull(themeChangeHandler);
    }

    private void sendStateToPreview() {
        BrowserUtil.postToPreview(iframe, BrowserUtil.OUTLINE_MESSAGE, outline);
        BrowserUtil.postToPreview(iframe, BrowserUtil.MEASURE_MESSAGE, measure);
        BrowserUtil.postToPreview(iframe, BrowserUtil.VISION_MESSAGE, vision != null
                ? vision.getId()
                : null);
    }

    // ---------- Viewport ----------

    private void toggleViewportMenu() {
        final Element button = Document.get().getElementById("wbm-viewport-button");
        final List<MenuItem> items = new ArrayList<>();
        items.add(MenuItem.of(RESET, "Reset viewport").icon("wbm-icon-refresh"));
        for (final ViewportPreset preset : ViewportPreset.values()) {
            items.add(MenuItem.of(preset.getId(), preset.getLabel())
                    .icon(preset.getIcon())
                    .active(preset == viewport && !customViewport));
        }
        Popover.toggle(button, MenuHtml.render(MenuItem.groups(items)), Popover.Align.START, target -> {
            final String id = MenuHtml.getClickedItemId(target);
            if (id == null) {
                return;
            }
            Popover.hideCurrent();
            if (RESET.equals(id)) {
                viewport = null;
                customViewport = false;
                applyViewport();
            } else {
                final ViewportPreset preset = ViewportPreset.fromId(id);
                viewport = preset;
                setViewportSize(preset.getWidth(), preset.getHeight(), false);
            }
        });
    }

    private void setViewportSize(final int width, final int height, final boolean custom) {
        viewportWidth = Math.max(MIN_VIEWPORT_SIZE, width);
        viewportHeight = Math.max(MIN_VIEWPORT_SIZE, height);
        customViewport = custom;
        applyViewport();
    }

    private void applyViewport() {
        final Element label = Document.get().getElementById("wbm-viewport-label");
        final Element button = Document.get().getElementById("wbm-viewport-button");
        if (viewport == null) {
            preview.removeClassName(CLASS_VIEWPORT);
            frame.getStyle().clearWidth();
            frame.getStyle().clearHeight();
            label.setInnerText("");
            button.removeClassName(CLASS_ACTIVE);
            button.setAttribute("aria-label", "Viewport size");
        } else {
            preview.addClassName(CLASS_VIEWPORT);
            frame.getStyle().setProperty("width", viewportWidth + "px");
            frame.getStyle().setProperty("height", viewportHeight + "px");
            final String name = customViewport
                    ? "Custom"
                    : viewport.getLabel();
            label.setInnerText(name);
            button.addClassName(CLASS_ACTIVE);
            button.setAttribute("aria-label", "Viewport size " + name);
            widthInput.setValue(viewportWidth + "px");
            heightInput.setValue(viewportHeight + "px");
        }
    }

    private void bindSizeInput(final InputElement input, final boolean width) {
        final Runnable apply = () -> {
            final Integer size = ViewportPreset.parseSize(input.getValue());
            if (size != null) {
                setViewportSize(width
                        ? size
                        : viewportWidth, width
                        ? viewportHeight
                        : size, true);
            } else {
                applyViewport();
            }
        };
        BrowserUtil.addListener(input, "change", event -> apply.run());
        BrowserUtil.addListener(input, "keydown", event -> {
            if (event.getKeyCode() == KeyCodes.KEY_ENTER) {
                apply.run();
            }
        });
    }

    private void bindViewportHandles() {
        BrowserUtil.addListener(frame, "mousedown", event -> {
            final Element target = Element.as(event.getEventTarget());
            final String side = target.getAttribute("data-side");
            if (side == null || side.isEmpty() || viewport == null) {
                return;
            }
            event.preventDefault();
            // Only one drag at a time, in case the last one never saw its mouse up
            endDrag();
            final int startX = event.getClientX();
            final int startY = event.getClientY();
            final int startWidth = viewportWidth;
            final int startHeight = viewportHeight;
            preview.addClassName(CLASS_DRAGGING);
            dragRegistration = Event.addNativePreviewHandler(nativePreview -> {
                final int type = nativePreview.getTypeInt();
                if (type == Event.ONMOUSEMOVE && BrowserUtil.getButtons(nativePreview.getNativeEvent()) == 0) {
                    // The button was released somewhere the mouse up wasn't seen
                    endDrag();
                } else if (type == Event.ONMOUSEMOVE) {
                    nativePreview.getNativeEvent().preventDefault();
                    final int dx = nativePreview.getNativeEvent().getClientX() - startX;
                    final int dy = nativePreview.getNativeEvent().getClientY() - startY;
                    setViewportSize(
                            "bottom".equals(side)
                                    ? startWidth
                                    : startWidth + dx,
                            "right".equals(side)
                                    ? startHeight
                                    : startHeight + dy,
                            true);
                } else if (type == Event.ONMOUSEUP) {
                    endDrag();
                }
            });
        });
    }

    private void endDrag() {
        preview.removeClassName(CLASS_DRAGGING);
        if (dragRegistration != null) {
            dragRegistration.removeHandler();
            dragRegistration = null;
        }
    }

    // ---------- Vision filter ----------

    private void toggleVisionMenu() {
        final Element button = Document.get().getElementById("wbm-vision-button");
        Popover.toggle(button, renderVisionMenu(), Popover.Align.START, target -> {
            final String id = MenuHtml.getClickedItemId(target);
            if (id == null) {
                return;
            }
            Popover.hideCurrent();
            vision = VisionFilter.fromId(id);
            final Element label = Document.get().getElementById("wbm-vision-label");
            label.setInnerText(vision != null
                    ? vision.getLabel()
                    : "");
            setActive("wbm-vision-button", vision != null);
            button.setAttribute("aria-label", vision != null
                    ? "Vision filter " + vision.getLabel()
                    : "Vision filter");
            sendStateToPreview();
        });
    }

    private SafeHtml renderVisionMenu() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"wbm-menu\" role=\"listbox\"><ul class=\"wbm-menu__group\">");
        appendOption(builder, RESET, "Reset colour filter", null, null, false);
        for (final VisionFilter filter : VisionFilter.values()) {
            appendOption(builder, filter.getId(), filter.getLabel(), filter.getDescription(),
                    filter.getCssFilter(), filter == vision);
        }
        builder.appendHtmlConstant("</ul></div>");
        return builder.toSafeHtml();
    }

    private static void appendOption(final SafeHtmlBuilder builder,
                                     final String id,
                                     final String label,
                                     final String description,
                                     final String swatchFilter,
                                     final boolean active) {
        builder.appendHtmlConstant("<li class=\"wbm-menu__item" + (active
                        ? " wbm-menu__item--active"
                        : "") + "\"><button type=\"button\" class=\"wbm-menu__button\" role=\"option\" "
                                   + MenuItem.ATTR_ID + "=\"")
                .appendEscaped(id)
                .appendHtmlConstant("\"><span class=\"wbm-menu__icon\">");
        if (swatchFilter == null) {
            MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-refresh");
        } else {
            // A rainbow showing the effect of the filter, as in Storybook
            builder.appendHtmlConstant("<span class=\"wbm-vision-swatch\" style=\"filter: ")
                    .appendEscaped(swatchFilter)
                    .appendHtmlConstant("\"></span>");
        }
        builder.appendHtmlConstant("</span><span class=\"wbm-menu__label wbm-menu__label--stacked\">")
                .appendEscaped(label);
        if (description != null) {
            builder.appendHtmlConstant("<small>").appendEscaped(description).appendHtmlConstant("</small>");
        }
        builder.appendHtmlConstant("</span></button></li>");
    }

    // ---------- Theme ----------

    private void toggleThemeMenu() {
        final Element button = Document.get().getElementById("wbm-theme-button");
        final List<MenuItem> items = new ArrayList<>();
        for (final StoryTheme option : StoryTheme.values()) {
            items.add(MenuItem.of(option.getId(), option.getLabel())
                    .icon("wbm-icon-theme")
                    .active(option == theme));
        }
        Popover.toggle(button, MenuHtml.render(MenuItem.groups(items)), Popover.Align.START, target -> {
            final String id = MenuHtml.getClickedItemId(target);
            if (id == null) {
                return;
            }
            Popover.hideCurrent();
            final StoryTheme chosen = StoryTheme.fromId(id);
            if (chosen != theme) {
                theme = chosen;
                BrowserUtil.setLocalStorage(THEME_STORAGE_KEY, theme == StoryTheme.DEFAULT
                        ? null
                        : theme.getId());
                showTheme();
                themeChangeHandler.run();
            }
        });
    }

    private void showTheme() {
        Document.get().getElementById("wbm-theme-label").setInnerText(theme.getLabel());
        Document.get().getElementById("wbm-theme-button").setAttribute("aria-label", "Theme " + theme.getLabel());
    }

    // ---------- Zoom ----------

    private void toggleZoomMenu() {
        final Element button = Document.get().getElementById("wbm-zoom");
        final Popover popover = Popover.toggle(button, renderZoomMenu(), Popover.Align.CENTER, target -> {
            final String id = MenuHtml.getClickedItemId(target);
            if (id == null) {
                // e.g. a click in the zoom field, which mustn't rebuild the menu
                return;
            }
            if (ZOOM_IN.equals(id)) {
                setZoom(ZoomLevels.zoomIn(zoom));
            } else if (ZOOM_OUT.equals(id)) {
                setZoom(ZoomLevels.zoomOut(zoom));
            } else if (id.startsWith(ZOOM_PREFIX)) {
                final Integer preset = ZoomLevels.parse(id.substring(ZOOM_PREFIX.length()));
                if (preset != null) {
                    setZoom(preset);
                }
            }
            refreshZoomMenu();
        });
        if (popover != null) {
            final InputElement input = popover.getElement().getElementsByTagName("input").getItem(0).cast();
            bindZoomInput(input);
            input.focus();
            input.select();
        }
    }

    private void refreshZoomMenu() {
        final Element button = Document.get().getElementById("wbm-zoom");
        if ("true".equals(button.getAttribute("aria-expanded"))) {
            Popover.hideCurrent();
            toggleZoomMenu();
        }
    }

    private void bindZoomInput(final InputElement input) {
        BrowserUtil.addListener(input, "keydown", event -> {
            if (event.getKeyCode() == KeyCodes.KEY_ENTER) {
                final Integer value = ZoomLevels.parse(input.getValue());
                if (value != null) {
                    setZoom(value);
                }
                input.setValue(zoom + "%");
                input.select();
            }
        });
    }

    private SafeHtml renderZoomMenu() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"wbm-zoom-menu\"><label class=\"wbm-zoom-menu__field\">");
        MenuHtml.appendIcon(builder, "wbm-icon", "wbm-icon-zoom");
        builder.appendHtmlConstant("<input aria-label=\"Zoom percentage\" spellcheck=\"false\" "
                                   + "autocomplete=\"off\" value=\"" + zoom + "%\"></label></div>");
        final List<MenuItem> items = new ArrayList<>();
        items.add(MenuItem.of(ZOOM_IN, "Zoom in").shortcut(KeyCombo.parse("alt+=")));
        items.add(MenuItem.of(ZOOM_OUT, "Zoom out").shortcut(KeyCombo.parse("alt+-")));
        for (final int preset : ZOOM_PRESETS) {
            final MenuItem item = MenuItem.of(ZOOM_PREFIX + preset, preset + "%").active(preset == zoom);
            if (preset == ZoomLevels.DEFAULT) {
                item.shortcut(KeyCombo.parse("alt+0"));
            }
            items.add(item);
        }
        builder.append(MenuHtml.render(MenuItem.groups(items)));
        return builder.toSafeHtml();
    }

    private void setZoom(final int percentage) {
        zoom = ZoomLevels.clamp(percentage);
        applyZoom();
    }

    private void applyZoom() {
        final double scale = zoom / 100.0;
        final String size = (100.0 / scale) + "%";
        zoomWrapper.getStyle().setProperty("width", size);
        zoomWrapper.getStyle().setProperty("height", size);
        zoomWrapper.getStyle().setProperty("transform", zoom == ZoomLevels.DEFAULT
                ? "none"
                : "scale(" + scale + ")");
        final Element button = Document.get().getElementById("wbm-zoom");
        button.setInnerText(zoom + "%");
        button.setAttribute("aria-checked", String.valueOf(zoom != ZoomLevels.DEFAULT));
    }

    // ---------- Helpers ----------

    private static void onClick(final String elementId, final Runnable action) {
        BrowserUtil.addListener(Document.get().getElementById(elementId), "click", event -> action.run());
    }

    private static void setActive(final String elementId, final boolean active) {
        final Element element = Document.get().getElementById(elementId);
        if (active) {
            element.addClassName(CLASS_ACTIVE);
        } else {
            element.removeClassName(CLASS_ACTIVE);
        }
        element.setAttribute("aria-checked", String.valueOf(active));
    }
}
