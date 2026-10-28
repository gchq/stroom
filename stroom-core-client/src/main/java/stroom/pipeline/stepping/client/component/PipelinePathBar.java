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

package stroom.pipeline.stepping.client.component;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.stepping.client.PipelinePath;
import stroom.pipeline.stepping.client.PipelinePath.Step;
import stroom.pipeline.stepping.client.PipelinePathCollapse;
import stroom.pipeline.stepping.client.SeverityChanges;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.pipeline.structure.client.component.AbstractPipelineTree;
import stroom.pipeline.structure.client.component.PipelineElementBoxFactory;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.contextmenu.client.event.ContextMenuEvent.Handler;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;
import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.tab.client.GlobalResizeObserver;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The stepping screen's pipeline bar: a row of chevrons, one for each element on a path through
/// the pipeline (see [PipelinePath]).
///
/// An element after a fork has a drop down arrow, which lists the elements the path could follow
/// there, marked if the branches not shown have active stepping filters. When the bar is too
/// narrow for its chevrons, those away from the selected element are collapsed into `…` chevrons
/// (see [PipelinePathCollapse]), which list them.
///
/// Each chevron holds a button that selects its element (or, for a `…` chevron, opens its list)
/// and, after a fork, a drop down chevron that is a menu button of its own, as a control can't be
/// both a button and a menu button. The bar is a toolbar, one tab stop: Left and Right (wrapping),
/// Home and End move between its controls; Enter or Space presses one; Down or Alt+Down opens a
/// chevron's list (from its button too); the context menu key or Shift+F10 opens the element's
/// context menu. A control's states are its ARIA attributes (`aria-current`, `aria-disabled`,
/// `aria-expanded`), which pipeline-path-bar.css styles.
public class PipelinePathBar extends AbstractPipelineTree {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String BASE_CLASS = "pipeline-path-bar";
    private static final String SEGMENT_CLASS = BASE_CLASS + "__segment";
    private static final String FIRST_CLASS = SEGMENT_CLASS + "--first";
    private static final String LAST_CLASS = SEGMENT_CLASS + "--last";
    private static final String COLLAPSED_CLASS = SEGMENT_CLASS + "--collapsed";
    private static final String ELLIPSIS_CLASS = SEGMENT_CLASS + "--ellipsis";
    private static final String SHAPE_CLASS = BASE_CLASS + "__shape";
    private static final String PULSE_CLASS = SEGMENT_CLASS + "--pulse";
    private static final String SEVERITY_CLASS = BASE_CLASS + "__severity";
    private static final String BRANCH_SEVERITY_CLASS = BASE_CLASS + "__branch-severity";
    private static final String BRANCH_SEVERITY_PULSE_CLASS = BRANCH_SEVERITY_CLASS + "--pulse";
    private static final String BUTTON_CLASS = BASE_CLASS + "__button";
    private static final String CONTENT_CLASS = BASE_CLASS + "__content";
    private static final String CARET_CLASS = BASE_CLASS + "__caret";
    private static final String ICON_CLASS = BASE_CLASS + "__icon";
    private static final String LABEL_CLASS = BASE_CLASS + "__label";
    private static final String LABEL_TEXT_CLASS = BASE_CLASS + "__label-text";
    private static final String DATA_TEXT_ATTRIBUTE = "data-text";
    private static final String FILTER_CLASS = BASE_CLASS + "__filter";
    private static final String BRANCH_FILTER_CLASS = BASE_CLASS + "__branch-filter";
    private static final String MENU_FILTER_CLASS = BASE_CLASS + "__menu-filter";
    private static final String MENU_SEVERITY_CLASS = BASE_CLASS + "__menu-severity";

    private static final String INDEX_ATTRIBUTE = "data-index";
    private static final String ELEMENT_ID_ATTRIBUTE = "data-element-id";
    private static final String ROLE = "role";
    private static final String TABINDEX = "tabindex";
    private static final String ARIA_LABEL = "aria-label";
    private static final String ARIA_CURRENT = "aria-current";
    private static final String ARIA_DISABLED = "aria-disabled";
    private static final String ARIA_HAS_POPUP = "aria-haspopup";
    private static final String ARIA_EXPANDED = "aria-expanded";
    private static final String ARIA_HIDDEN = "aria-hidden";
    // The bar's controls: each chevron's button, and the drop down chevron of each after a fork
    private static final String BUTTON_SELECTOR = "[role=button]";
    // How the control that had the focus is found again after the bar is redrawn
    private static final String CARET_KEY_SUFFIX = "#caret";
    private static final String ELLIPSIS_TEXT = "…";
    // The gap between the menu and the chevron it drops down from
    private static final int MENU_GAP = 2;
    // How far in from a menu's left its items' icons are, so that a menu can line them up under a
    // chevron's icon
    private static final int MENU_ICON_INSET = 8;
    // How long an element pulses when a step gives it an error: three pulses of 1.4s, as the
    // pulse animations in pipeline-path-bar.css
    private static final double PULSE_MILLIS = 3 * 1400;
    // How far into its pulse a redrawn element is, as a negative delay (pipeline-path-bar.css)
    private static final String PULSE_DELAY = "--pipeline-path-bar__pulse-delay";

    @UiField
    DivElement bar;
    // Tells screen reader users which elements a step has given new or worse problems
    @UiField
    DivElement liveRegion;

    // When each element with a new or worse error began pulsing, keyed by its ID
    private final Map<String, Double> pulseStarts = new HashMap<>();
    // The fork choices, keyed by the forking element's ID, each the ID of the child followed
    private final Map<String, String> choices = new HashMap<>();
    // The chevrons of the path's steps, in order, then the `…` chevrons, each with the indexes of
    // the steps it collapses
    private final List<Element> segments = new ArrayList<>();
    private final Map<Element, List<Integer>> ellipses = new HashMap<>();

    private PipelineModel pipelineModel;
    private DefaultTreeForTreeLayout<PipelineElement> tree;
    private Map<String, Severity> severities = Collections.emptyMap();
    private List<PipelineElement> disabledElements = Collections.emptyList();
    private ShowMenu onShowMenu;
    private PipelinePath path;
    private List<Step> steps = Collections.emptyList();
    // The selected element when the bar was last drawn
    private PipelineElement selected;
    private int laidOutWidth = -1;
    // Asking the selection model for the selection can fire its pending change, which redraws the
    // bar, so a redraw asked for while drawing is done once the drawing is finished
    private boolean rendering;
    private boolean renderRequested;

    /// Makes the bar, with no pipeline yet.
    public PipelinePathBar() {
        initWidget(BINDER.createAndBindUi(this));

        // The bar's events bubble to its root
        addDomHandler(this::onClick, ClickEvent.getType());
        addDomHandler(this::onKeyDown, KeyDownEvent.getType());
        addDomHandler(this::onContextMenu, com.google.gwt.event.dom.client.ContextMenuEvent.getType());
        addAttachHandler(event -> {
            if (event.isAttached()) {
                GlobalResizeObserver.addListener(bar, element -> onResize());
                Scheduler.get().scheduleDeferred(this::layout);
            } else {
                GlobalResizeObserver.removeListener(bar);
            }
        });
    }

    /// Sets what shows the bar's drop down menus, e.g. the branches at a fork: the bar's
    /// controller ([PipelinePathBarController]).
    ///
    /// @param onShowMenu Shows a menu.
    public void setOnShowMenu(final ShowMenu onShowMenu) {
        this.onShowMenu = onShowMenu;
    }

    @Override
    protected void showPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    @Override
    protected void showTree(final DefaultTreeForTreeLayout<PipelineElement> tree) {
        this.tree = tree;
    }

    @Override
    public void redraw() {
        render();
    }

    @Override
    public void setAllowNullSelection(final boolean allowNullSelection) {
        // Clicking a chevron always selects its element
    }

    @Override
    public int getTreeHeight() {
        return bar.getOffsetHeight();
    }

    @Override
    protected void showDisabledElements(final List<PipelineElement> disabledElements) {
        this.disabledElements = NullSafe.list(disabledElements);
        render();
    }

    @Override
    public void setElementSeverities(final Map<String, Severity> elementIdToSeveritiesMap) {
        final Map<String, Severity> newSeverities = NullSafe.map(elementIdToSeveritiesMap);
        final Set<String> worsened = SeverityChanges.getWorsened(severities, newSeverities, Severity.WARNING);

        // New or worse errors pulse; warnings have a steady border
        final double now = System.currentTimeMillis();
        for (final String elementId : worsened) {
            if (newSeverities.get(elementId).greaterThanOrEqual(Severity.ERROR)) {
                pulseStarts.put(elementId, now);
            }
        }
        pulseStarts.keySet().removeIf(elementId -> !isError(newSeverities.get(elementId)));

        this.severities = newSeverities;
        announce(worsened);
        render();
    }

    // Tells screen reader users which elements now have new or worse problems, in pipeline order,
    // e.g. "xmlWriter has errors. jsonWriter has warnings."
    private void announce(final Set<String> worsened) {
        if (worsened.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final PipelineElement element : getAllElements()) {
            final String elementId = element.getElementId().getId();
            if (worsened.contains(elementId)) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(element.getDisplayName())
                        .append(" has ")
                        .append(severities.get(elementId).getSummaryValue().toLowerCase())
                        .append('.');
            }
        }
        // Emptied first so that the same message is announced again
        liveRegion.setInnerText("");
        final String message = sb.toString();
        Scheduler.get().scheduleDeferred(() -> liveRegion.setInnerText(message));
    }

    // The pipeline's elements, from its source, each before its children
    private List<PipelineElement> getAllElements() {
        final List<PipelineElement> elements = new ArrayList<>();
        final PipelineElement source = NullSafe.get(tree, DefaultTreeForTreeLayout::getRoot);
        if (source != null) {
            addElements(source, elements);
        }
        return elements;
    }

    private void addElements(final PipelineElement element, final List<PipelineElement> elements) {
        elements.add(element);
        for (final PipelineElement child : getChildren(element)) {
            addElements(child, elements);
        }
    }

    // How long ago the element began pulsing, or -1 if it isn't pulsing
    private double getPulseElapsed(final PipelineElement element) {
        final Double start = pulseStarts.get(element.getElementId().getId());
        if (start == null) {
            return -1;
        }
        final double elapsed = System.currentTimeMillis() - start;
        return elapsed < PULSE_MILLIS
                ? elapsed
                : -1;
    }

    // How long ago the latest pulse in a branch began, or -1 if nothing in it is pulsing
    private double getBranchPulseElapsed(final PipelineElement element) {
        double latest = getPulseElapsed(element);
        for (final PipelineElement child : getChildren(element)) {
            final double elapsed = getBranchPulseElapsed(child);
            if (elapsed >= 0 && (latest < 0 || elapsed < latest)) {
                latest = elapsed;
            }
        }
        return latest;
    }

    private static boolean isError(final Severity severity) {
        return severity != null && severity.greaterThanOrEqual(Severity.ERROR);
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final Handler handler) {
        return addHandler(handler, ContextMenuEvent.getType());
    }

    // Draws the path's chevrons, keeping the keyboard focus on the element that had it
    private void render() {
        if (rendering) {
            renderRequested = true;
            return;
        }
        rendering = true;
        try {
            do {
                renderRequested = false;
                draw();
            } while (renderRequested);
        } finally {
            rendering = false;
        }
    }

    private void draw() {
        final String focusedKey = getKey(getFocusedControl());

        clearSegments();
        final PipelineElement root = NullSafe.get(tree, DefaultTreeForTreeLayout::getRoot);
        if (root == null) {
            path = null;
            steps = Collections.emptyList();
            return;
        }

        selected = getSelected();
        path = new PipelinePath(
                root,
                this::getChildren,
                element -> severities.get(element.getElementId().getId()),
                this::hasActiveFilters);
        path.rememberPathTo(selected, choices);
        steps = path.follow(selected, choices);

        for (int i = 0; i < steps.size(); i++) {
            final Element segment = createSegment(steps.get(i), i);
            segments.add(segment);
            bar.appendChild(segment);
        }
        laidOutWidth = -1;
        layout();

        // The tab stop is the control that had the focus, else the selected element's button
        Element tabStop = findControl(focusedKey);
        if (tabStop == null || isCollapsed(tabStop)) {
            tabStop = getButton(findSegment(NullSafe.get(selected, PipelineElement::getId)));
        }
        setTabStop(tabStop);
        if (focusedKey != null && tabStop != null) {
            tabStop.focus();
        }
    }

    private void clearSegments() {
        segments.clear();
        ellipses.clear();
        bar.removeAllChildren();
    }

    // A chevron: its shape, its button and, after a fork, its drop down chevron
    private Element createSegment(final Step step, final int index) {
        final PipelineElement element = step.element();
        final Severity severity = severities.get(element.getElementId().getId());
        final boolean disabled = isDisabled(element);
        final boolean filters = hasActiveFilters(element);

        final Element segment = Document.get().createDivElement();
        segment.setClassName(SEGMENT_CLASS);
        if (index == 0) {
            segment.addClassName(FIRST_CLASS);
        }
        if (index == steps.size() - 1) {
            segment.addClassName(LAST_CLASS);
        }
        segment.setAttribute(INDEX_ATTRIBUTE, String.valueOf(index));
        segment.setAttribute(ELEMENT_ID_ATTRIBUTE, element.getId());
        segment.appendChild(createShape());
        setSeverity(segment, severity, getPulseElapsed(element));

        final String description = describe(element, severity, filters, disabled);
        final Element button = createButton(description);
        final Element content = createContent(description);
        if (element.equals(selected)) {
            button.setAttribute(ARIA_CURRENT, "true");
        }
        if (disabled) {
            button.setAttribute(ARIA_DISABLED, "true");
        }
        final SvgImage icon = NullSafe.get(getElementType(element), PipelineElementType::getIcon);
        if (icon != null) {
            content.appendChild(createIcon(icon, ICON_CLASS));
        }
        content.appendChild(createLabel(element.getDisplayName()));
        final SvgImage severityIcon = getSeverityIcon(severity);
        if (severityIcon != null) {
            content.appendChild(createIcon(severityIcon, SEVERITY_CLASS));
        }
        if (filters) {
            content.appendChild(createIcon(SvgImage.FILTER, FILTER_CLASS));
        }
        button.appendChild(content);
        segment.appendChild(button);

        if (step.isAfterFork()) {
            // Whether the branches not shown have filters is for sight only here: the drop down
            // chevron's name says so
            if (step.hiddenFilters()) {
                segment.appendChild(createIcon(SvgImage.FILTER, BRANCH_FILTER_CLASS));
            }
            segment.appendChild(createCaret(step));
        }
        return segment;
    }

    // A control of the bar, named by its description
    private static Element createButton(final String description) {
        final Element button = Document.get().createSpanElement();
        button.setClassName(BUTTON_CLASS);
        button.setAttribute(ROLE, "button");
        button.setAttribute(TABINDEX, "-1");
        button.setAttribute(ARIA_LABEL, description);
        return button;
    }

    // What a control shows, with its description as a hover tip. It is hidden from screen readers,
    // as a title on the control itself would be read again after its name, as its description.
    private static Element createContent(final String description) {
        final Element content = Document.get().createSpanElement();
        content.setClassName(CONTENT_CLASS);
        content.setAttribute(ARIA_HIDDEN, "true");
        content.setTitle(description);
        return content;
    }

    // A chevron's name. It holds its text in data-text too, so that it can keep room for its text
    // in bold (see pipeline-path-bar.css)
    private static Element createLabel(final String text) {
        final Element label = Document.get().createSpanElement();
        label.setClassName(LABEL_CLASS);
        label.setAttribute(DATA_TEXT_ATTRIBUTE, text);
        final Element labelText = Document.get().createSpanElement();
        labelText.setClassName(LABEL_TEXT_CLASS);
        labelText.setInnerText(text);
        label.appendChild(labelText);
        return label;
    }

    // The chevron's shape, under its content: its border, and its fill (its ::before, see
    // pipeline-path-bar.css)
    private static Element createShape() {
        final Element shape = Document.get().createSpanElement();
        shape.setClassName(SHAPE_CLASS);
        shape.setAttribute(ARIA_HIDDEN, "true");
        return shape;
    }

    // Gives a chevron a thicker border in the colour of a severity of warnings or worse, pulsing if
    // it began pulsing the given number of milliseconds ago (or not if negative). The pulse is
    // carried on from where it had got to, as the chevron may have been redrawn while it pulsed.
    private static void setSeverity(final Element segment, final Severity severity, final double pulseElapsed) {
        final String severityClass = getSeverityClass(SEGMENT_CLASS, severity);
        if (severityClass != null) {
            segment.addClassName(severityClass);
            if (pulseElapsed >= 0) {
                segment.addClassName(PULSE_CLASS);
                final Element shape = segment.getFirstChildElement();
                CssProperties.set(shape, PULSE_DELAY, -Math.round(pulseElapsed) + "ms");
            }
        }
    }

    // The drop down chevron of an element after a fork: a menu button listing the branches the
    // path could follow there. The icon of the worst severity in the branches not shown is a badge
    // on it, so that it isn't taken for its element's own; it is for sight only, as its name says
    // so.
    private Element createCaret(final Step step) {
        final Element caret = createButton(describeCaret(step));
        caret.setClassName(CARET_CLASS);
        caret.setAttribute(ARIA_HAS_POPUP, "menu");
        caret.setAttribute(ARIA_EXPANDED, "false");
        final Element arrow = createContent(describeCaret(step));
        arrow.setClassName(CARET_CLASS + "-target");
        arrow.appendChild(createIcon(SvgImage.ARROW_DOWN, CARET_CLASS + "-arrow"));
        final SvgImage hiddenSeverityIcon = getSeverityIcon(step.hiddenSeverity());
        if (hiddenSeverityIcon != null) {
            final Element badge = createIcon(hiddenSeverityIcon, BRANCH_SEVERITY_CLASS);
            final double pulseElapsed = getHiddenPulseElapsed(step);
            if (pulseElapsed >= 0) {
                badge.addClassName(BRANCH_SEVERITY_PULSE_CLASS);
                CssProperties.set(badge, PULSE_DELAY, -Math.round(pulseElapsed) + "ms");
            }
            arrow.appendChild(badge);
        }
        caret.appendChild(arrow);
        return caret;
    }

    // How long ago the latest pulse in the branches not shown at a fork began, or -1 for none
    private double getHiddenPulseElapsed(final Step step) {
        double latest = -1;
        for (final PipelineElement alternative : step.alternatives()) {
            if (!alternative.equals(step.element())) {
                final double elapsed = getBranchPulseElapsed(alternative);
                if (elapsed >= 0 && (latest < 0 || elapsed < latest)) {
                    latest = elapsed;
                }
            }
        }
        return latest;
    }

    // The standard icon for a severity of warnings or worse, as the stepping screen's list of
    // markers uses (a fatal error's in the form that shows on dark backgrounds too), or null for
    // none or information
    private static SvgImage getSeverityIcon(final Severity severity) {
        if (severity == null || severity.lessThan(Severity.WARNING)) {
            return null;
        }
        return switch (severity) {
            case WARNING -> SvgImage.ALERT;
            case ERROR -> SvgImage.ERROR;
            default -> SvgImage.FATAL_DARK;
        };
    }

    // The class for a severity of warnings or worse, or null for none or information
    private static String getSeverityClass(final String baseClass, final Severity severity) {
        if (severity == null || severity.lessThan(Severity.WARNING)) {
            return null;
        }
        return switch (severity) {
            case WARNING -> baseClass + "--warn";
            case ERROR -> baseClass + "--error";
            default -> baseClass + "--fatal";
        };
    }

    // An icon for sight only: the chevron's name describes what it shows
    private static Element createIcon(final SvgImage icon, final String className) {
        final Element holder = Document.get().createSpanElement();
        holder.setInnerSafeHtml(SvgImageUtil.toSafeHtml(icon, "svgIcon", className));
        final Element iconElement = holder.getFirstChildElement();
        iconElement.setAttribute(ARIA_HIDDEN, "true");
        return iconElement;
    }

    // e.g. "xmlWriter, XML Writer, has errors"
    private String describe(final PipelineElement element,
                            final Severity severity,
                            final boolean filters,
                            final boolean disabled) {
        final StringBuilder sb = new StringBuilder(element.getDisplayName());
        final String typeName = PipelineElementBoxFactory.getIconName(getElementType(element));
        if (typeName != null && !typeName.equals(element.getDisplayName())) {
            sb.append(", ").append(typeName);
        }
        if (severity != null) {
            sb.append(", has ").append(severity.getSummaryValue().toLowerCase());
        }
        if (filters) {
            sb.append(", has stepping filters");
        }
        if (disabled) {
            sb.append(", not stepped");
        }
        return sb.toString();
    }

    // e.g. "Choose branch, xmlWriter is 1 of 2, other branches have warnings"
    private static String describeCaret(final Step step) {
        final StringBuilder sb = new StringBuilder("Choose branch, ")
                .append(step.element().getDisplayName())
                .append(" is ")
                .append(step.alternatives().indexOf(step.element()) + 1)
                .append(" of ")
                .append(step.alternatives().size());
        if (step.hiddenSeverity() != null) {
            sb.append(", other branches have ")
                    .append(step.hiddenSeverity().getSummaryValue().toLowerCase());
        }
        if (step.hiddenFilters()) {
            sb.append(", other branches have stepping filters");
        }
        return sb.toString();
    }

    // Collapses the chevrons that don't fit into `…` chevrons
    private void layout() {
        if (!isAttached() || segments.isEmpty()) {
            return;
        }
        final int available = bar.getClientWidth();
        if (available <= 0) {
            return;
        }
        laidOutWidth = available;

        // Show them all to measure them
        for (final Element ellipsis : ellipses.keySet()) {
            ellipsis.removeFromParent();
        }
        ellipses.clear();
        final int[] widths = new int[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            final Element segment = segments.get(i);
            segment.removeClassName(COLLAPSED_CLASS);
            widths[i] = getLayoutWidth(segment);
        }
        final int needed = PipelinePathCollapse.getWidth(widths, new boolean[widths.length], 0);
        if (needed <= available) {
            return;
        }

        final Element measure = createEllipsis(Collections.emptyList());
        bar.appendChild(measure);
        final int ellipsisWidth = getLayoutWidth(measure);
        measure.removeFromParent();

        final boolean[] collapsed = PipelinePathCollapse.collapse(
                widths, available, getSelectedIndex(), ellipsisWidth);
        List<Integer> run = null;
        for (int i = 0; i < collapsed.length; i++) {
            if (collapsed[i]) {
                segments.get(i).addClassName(COLLAPSED_CLASS);
                if (run == null) {
                    run = new ArrayList<>();
                }
                run.add(i);
            } else if (run != null) {
                addEllipsis(run);
                run = null;
            }
        }
        if (run != null) {
            addEllipsis(run);
        }
    }

    // Adds a `…` chevron in place of a run of collapsed chevrons
    private void addEllipsis(final List<Integer> run) {
        final Element ellipsis = createEllipsis(run);
        bar.insertBefore(ellipsis, segments.get(run.get(0)));
        ellipses.put(ellipsis, run);
    }

    private Element createEllipsis(final List<Integer> run) {
        Severity worst = null;
        boolean filters = false;
        double pulseElapsed = -1;
        for (final int index : run) {
            final Step step = steps.get(index);
            worst = Severity.getMaxSeverity(worst, severities.get(step.element().getElementId().getId()))
                    .orElse(null);
            worst = Severity.getMaxSeverity(worst, step.hiddenSeverity()).orElse(null);
            filters = filters || hasActiveFilters(step.element()) || step.hiddenFilters();
            for (final double elapsed : new double[]{getPulseElapsed(step.element()), getHiddenPulseElapsed(step)}) {
                if (elapsed >= 0 && (pulseElapsed < 0 || elapsed < pulseElapsed)) {
                    pulseElapsed = elapsed;
                }
            }
        }

        final StringBuilder description = new StringBuilder()
                .append(run.size())
                .append(run.size() == 1
                        ? " more element"
                        : " more elements");
        if (worst != null) {
            description.append(", with ").append(worst.getSummaryValue().toLowerCase());
        }
        if (filters) {
            description.append(", with stepping filters");
        }

        // It has the border of the worst severity it collapses, and pulses if any of it pulses.
        // Its button is a menu button, listing the elements it collapses.
        final Element ellipsis = Document.get().createDivElement();
        ellipsis.setClassName(SEGMENT_CLASS + " " + ELLIPSIS_CLASS);
        ellipsis.appendChild(createShape());
        setSeverity(ellipsis, worst, pulseElapsed);
        final Element button = createButton(description.toString());
        button.setAttribute(ARIA_HAS_POPUP, "menu");
        button.setAttribute(ARIA_EXPANDED, "false");
        final Element content = createContent(description.toString());
        content.appendChild(createLabel(ELLIPSIS_TEXT));
        final SvgImage severityIcon = getSeverityIcon(worst);
        if (severityIcon != null) {
            content.appendChild(createIcon(severityIcon, SEVERITY_CLASS));
        }
        if (filters) {
            content.appendChild(createIcon(SvgImage.FILTER, FILTER_CLASS));
        }
        button.appendChild(content);
        ellipsis.appendChild(button);
        return ellipsis;
    }

    private void onResize() {
        if (bar.getClientWidth() != laidOutWidth) {
            final Element focused = getFocusedControl();
            layout();
            final Element tabStop = getTabStop();
            if (tabStop == null || isCollapsed(tabStop)) {
                final Element selectedButton = getButton(findSegment(NullSafe.get(selected, PipelineElement::getId)));
                setTabStop(selectedButton);
                if (focused != null && selectedButton != null) {
                    selectedButton.focus();
                }
            }
        }
    }

    // A click anywhere on a chevron presses its button, except on its drop down chevron
    private void onClick(final ClickEvent event) {
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element segment = closest(target, "." + SEGMENT_CLASS);
        if (segment == null) {
            return;
        }
        final Element caret = closest(target, "." + CARET_CLASS);
        if (caret != null) {
            setTabStop(caret);
            showBranchMenu(segment);
        } else if (ellipses.containsKey(segment)) {
            setTabStop(getButton(segment));
            showEllipsisMenu(segment);
        } else {
            setTabStop(getButton(segment));
            select(getStep(segment));
        }
    }

    private void onKeyDown(final KeyDownEvent event) {
        if (event.isControlKeyDown() || event.isMetaKeyDown()) {
            return;
        }
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element control = closest(target, BUTTON_SELECTOR);
        final Element segment = closest(target, "." + SEGMENT_CLASS);
        if (control == null || segment == null) {
            return;
        }
        final boolean isMenuButton = control.hasClassName(CARET_CLASS) || ellipses.containsKey(segment);
        final int keyCode = event.getNativeKeyCode();
        final boolean handled;
        if (keyCode == KeyCodes.KEY_CONTEXT_MENU || (keyCode == KeyCodes.KEY_F10 && event.isShiftKeyDown())) {
            showContextMenu(segment, null);
            handled = true;
        } else if (event.isShiftKeyDown()) {
            handled = false;
        } else if (keyCode == KeyCodes.KEY_DOWN) {
            // A chevron's button opens its drop down chevron's list too
            handled = showMenu(segment);
        } else if (event.isAltKeyDown()) {
            handled = false;
        } else if (keyCode == KeyCodes.KEY_LEFT
                   || keyCode == KeyCodes.KEY_RIGHT
                   || keyCode == KeyCodes.KEY_HOME
                   || keyCode == KeyCodes.KEY_END) {
            moveFocus(control, keyCode);
            handled = true;
        } else if (keyCode == KeyCodes.KEY_ENTER || keyCode == KeyCodes.KEY_SPACE) {
            if (isMenuButton) {
                showMenu(segment);
            } else {
                select(getStep(segment));
            }
            handled = true;
        } else {
            handled = false;
        }
        if (handled) {
            event.preventDefault();
            event.stopPropagation();
        }
    }

    private void onContextMenu(final com.google.gwt.event.dom.client.ContextMenuEvent event) {
        final Element target = event.getNativeEvent().getEventTarget().cast();
        final Element segment = closest(target, "." + SEGMENT_CLASS);
        if (segment != null && !ellipses.containsKey(segment)) {
            event.preventDefault();
            final NativeEvent nativeEvent = event.getNativeEvent();
            showContextMenu(segment, new PopupPosition(nativeEvent.getClientX(), nativeEvent.getClientY()));
        }
    }

    // Selects the element and opens its context menu, at the position, or below the chevron if
    // null
    private void showContextMenu(final Element segment, final PopupPosition position) {
        final Step step = getStep(segment);
        if (step == null || isDisabled(step.element())) {
            return;
        }
        select(step);
        final Element current = findSegment(step.element().getId());
        final PopupPosition popupPosition = position != null
                ? position
                : new PopupPosition(new Rect(current != null
                        ? current
                        : segment).growY(MENU_GAP), PopupLocation.BELOW);
        ContextMenuEvent.fire(this, popupPosition);
    }

    // Opens the chevron's list, if it has one
    private boolean showMenu(final Element segment) {
        if (ellipses.containsKey(segment)) {
            showEllipsisMenu(segment);
            return true;
        }
        final Step step = getStep(segment);
        if (step != null && step.isAfterFork()) {
            showBranchMenu(segment);
            return true;
        }
        return false;
    }

    // Lists the elements the path could follow at a fork, from the chevron's drop down chevron
    private void showBranchMenu(final Element segment) {
        final int index = getIndex(segment);
        final Element caret = getCaret(segment);
        if (index <= 0 || caret == null || onShowMenu == null) {
            return;
        }
        final Step step = steps.get(index);
        final PipelineElement parent = steps.get(index - 1).element();
        final List<Item> items = new ArrayList<>();
        Item currentItem = null;
        for (int i = 0; i < step.alternatives().size(); i++) {
            final PipelineElement alternative = step.alternatives().get(i);
            final boolean current = alternative.equals(step.element());
            final Item item = new IconMenuItem.Builder()
                    .priority(i)
                    .icon(NullSafe.get(getElementType(alternative), PipelineElementType::getIcon))
                    .text(getMenuText(alternative,
                            path.getBranchSeverity(alternative),
                            path.hasBranchFilters(alternative),
                            current,
                            true))
                    .highlight(current)
                    .command(() -> follow(parent, alternative))
                    .build();
            items.add(item);
            if (current) {
                currentItem = item;
            }
        }
        // As a menu that chooses one of its items, it starts on the current choice
        openMenu(segment, caret, items, currentItem);
    }

    // Lists the elements a `…` chevron collapses
    private void showEllipsisMenu(final Element ellipsis) {
        if (onShowMenu == null) {
            return;
        }
        final List<Item> items = new ArrayList<>();
        for (final int index : ellipses.get(ellipsis)) {
            final Step step = steps.get(index);
            final PipelineElement element = step.element();
            items.add(new IconMenuItem.Builder()
                    .priority(index)
                    .icon(NullSafe.get(getElementType(element), PipelineElementType::getIcon))
                    .text(getMenuText(element,
                            severities.get(element.getElementId().getId()),
                            hasActiveFilters(element),
                            false,
                            false))
                    .enabled(!isDisabled(element))
                    .command(() -> {
                        select(step);
                        focusSegmentLater(element.getId());
                    })
                    .build());
        }
        openMenu(ellipsis, getButton(ellipsis), items, null);
    }

    // Opens a menu below a chevron, from its menu button, which says it is expanded until the menu
    // closes. The menu's icons line up under the chevron's icon (or, with none, its name).
    private void openMenu(final Element segment,
                          final Element opener,
                          final List<Item> items,
                          final Item focusItem) {
        opener.setAttribute(ARIA_EXPANDED, "true");
        Element alignTo = getDescendant(segment, ICON_CLASS);
        if (alignTo == null) {
            alignTo = getDescendant(segment, LABEL_CLASS);
        }
        final Rect segmentRect = new Rect(segment);
        final double left = (alignTo != null
                ? alignTo.getAbsoluteLeft()
                : segmentRect.getLeft()) - MENU_ICON_INSET;
        final Rect below = new Rect(segmentRect.getTop(), segmentRect.getBottom(), left, left + 1)
                .growY(MENU_GAP);
        onShowMenu.show(
                items,
                new PopupPosition(below, PopupLocation.BELOW),
                opener,
                focusItem,
                () -> opener.setAttribute(ARIA_EXPANDED, "false"));
    }

    private static Element getDescendant(final Element element, final String className) {
        final NodeList<Element> found = element.getElementsByTagName("*");
        for (int i = 0; i < found.getLength(); i++) {
            if (found.getItem(i).hasClassName(className)) {
                return found.getItem(i);
            }
        }
        return null;
    }

    // The element's name, then, as on a chevron, the icon of a severity of warnings or worse and a
    // filter icon if it has filters. Each is in words for screen readers, as is whether it is the
    // branch the path follows (the menu's highlight is only seen). For a branch (a branch menu's
    // item), the severity and filters are those anywhere in the branch, which the words say, so
    // that they aren't taken for the element's own.
    private static SafeHtml getMenuText(final PipelineElement element,
                                        final Severity severity,
                                        final boolean filters,
                                        final boolean current,
                                        final boolean branch) {
        final String has = branch
                ? ", branch has "
                : ", has ";
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendEscaped(element.getDisplayName());
        if (current) {
            appendScreenReaderText(builder, ", current branch");
        }
        final SvgImage severityIcon = getSeverityIcon(severity);
        if (severityIcon != null) {
            builder.appendHtmlConstant("<span class=\"" + MENU_SEVERITY_CLASS + "\" aria-hidden=\"true\">");
            builder.append(SvgImageUtil.toSafeHtml(severityIcon, "svgIcon"));
            builder.appendHtmlConstant("</span>");
            appendScreenReaderText(builder, has + severity.getSummaryValue().toLowerCase());
        }
        if (filters) {
            builder.appendHtmlConstant("<span class=\"" + MENU_FILTER_CLASS + "\" aria-hidden=\"true\">");
            builder.append(SvgImageUtil.toSafeHtml(SvgImage.FILTER, "svgIcon"));
            builder.appendHtmlConstant("</span>");
            appendScreenReaderText(builder, has + "stepping filters");
        }
        return builder.toSafeHtml();
    }

    private static void appendScreenReaderText(final SafeHtmlBuilder builder, final String text) {
        builder.appendHtmlConstant("<span class=\"sr-only\">");
        builder.appendEscaped(text);
        builder.appendHtmlConstant("</span>");
    }

    // Follows another branch at a fork, selecting its element (or, if it can't be selected, the
    // first one after it that can be, if the selection would otherwise keep the path where it is)
    private void follow(final PipelineElement parent, final PipelineElement alternative) {
        choices.put(parent.getId(), alternative.getId());
        if (!isDisabled(alternative)) {
            getSelectionModel().setSelected(alternative, true);
        } else {
            final PipelineElement selectable = findSelectable(alternative);
            final boolean selectionElsewhere = path.follow(selected, choices).stream()
                    .noneMatch(step -> step.element().equals(alternative));
            if (selectable != null && selectionElsewhere) {
                choices.remove(parent.getId());
                getSelectionModel().setSelected(selectable, true);
            }
        }
        render();
        focusSegmentLater(alternative.getId());
    }

    private PipelineElement findSelectable(final PipelineElement element) {
        if (!isDisabled(element)) {
            return element;
        }
        for (final PipelineElement child : getChildren(element)) {
            final PipelineElement selectable = findSelectable(child);
            if (selectable != null) {
                return selectable;
            }
        }
        return null;
    }

    private void select(final Step step) {
        if (step != null && !isDisabled(step.element())) {
            getSelectionModel().setSelected(step.element(), true);
        }
    }

    // Focuses an element's button once the menu that chose it has closed (and given the focus
    // back to the control that opened it, which may since have been redrawn)
    private void focusSegmentLater(final String elementId) {
        Scheduler.get().scheduleDeferred(() -> {
            final Element button = getButton(findSegment(elementId));
            if (button != null && !isCollapsed(button)) {
                setTabStop(button);
                button.focus();
            }
        });
    }

    private void moveFocus(final Element from, final int keyCode) {
        final List<Element> controls = getShownControls();
        final int index = controls.indexOf(from);
        if (index == -1) {
            return;
        }
        final int last = controls.size() - 1;
        final int next;
        if (keyCode == KeyCodes.KEY_RIGHT) {
            next = index == last
                    ? 0
                    : index + 1;
        } else if (keyCode == KeyCodes.KEY_LEFT) {
            next = index == 0
                    ? last
                    : index - 1;
        } else if (keyCode == KeyCodes.KEY_HOME) {
            next = 0;
        } else {
            next = last;
        }
        final Element target = controls.get(next);
        setTabStop(target);
        target.focus();
    }

    // The controls of the chevrons shown, in order: each one's button, then its drop down chevron
    private List<Element> getShownControls() {
        final List<Element> controls = new ArrayList<>();
        Element child = bar.getFirstChildElement();
        while (child != null) {
            if (child.hasClassName(SEGMENT_CLASS) && !child.hasClassName(COLLAPSED_CLASS)) {
                final Element button = getButton(child);
                if (button != null) {
                    controls.add(button);
                }
                final Element caret = getCaret(child);
                if (caret != null) {
                    controls.add(caret);
                }
            }
            child = child.getNextSiblingElement();
        }
        return controls;
    }

    // Makes the control the bar's one tab stop
    private void setTabStop(final Element tabStop) {
        final List<Element> controls = getShownControls();
        final Element stop = tabStop != null && controls.contains(tabStop)
                ? tabStop
                : controls.isEmpty()
                        ? null
                        : controls.get(0);
        for (final Element control : controls) {
            control.setAttribute(TABINDEX, control == stop
                    ? "0"
                    : "-1");
        }
    }

    private Element getTabStop() {
        for (final Element control : getShownControls()) {
            if ("0".equals(control.getAttribute(TABINDEX))) {
                return control;
            }
        }
        return null;
    }

    private Element getFocusedControl() {
        final Element active = getActiveElement();
        return active != null && bar.isOrHasChild(active)
                ? closest(active, BUTTON_SELECTOR)
                : null;
    }

    // Identifies a control by its element's ID (and whether it is the drop down chevron), so that
    // it can be found again once the bar is redrawn; null for none or a `…` chevron's
    private static String getKey(final Element control) {
        if (control == null) {
            return null;
        }
        final Element segment = closest(control, "." + SEGMENT_CLASS);
        final String elementId = segment == null
                ? null
                : segment.getAttribute(ELEMENT_ID_ATTRIBUTE);
        if (NullSafe.isBlankString(elementId)) {
            return null;
        }
        return control.hasClassName(CARET_CLASS)
                ? elementId + CARET_KEY_SUFFIX
                : elementId;
    }

    private Element findControl(final String key) {
        if (key == null) {
            return null;
        }
        if (key.endsWith(CARET_KEY_SUFFIX)) {
            return getCaret(findSegment(key.substring(0, key.length() - CARET_KEY_SUFFIX.length())));
        }
        return getButton(findSegment(key));
    }

    private static Element getButton(final Element segment) {
        return getChild(segment, BUTTON_CLASS);
    }

    private static Element getCaret(final Element segment) {
        return getChild(segment, CARET_CLASS);
    }

    private static Element getChild(final Element segment, final String className) {
        if (segment != null) {
            Element child = segment.getFirstChildElement();
            while (child != null) {
                if (child.hasClassName(className)) {
                    return child;
                }
                child = child.getNextSiblingElement();
            }
        }
        return null;
    }

    private Element findSegment(final String elementId) {
        if (elementId != null) {
            for (final Element segment : segments) {
                if (elementId.equals(segment.getAttribute(ELEMENT_ID_ATTRIBUTE))) {
                    return segment;
                }
            }
        }
        return null;
    }

    private Step getStep(final Element segment) {
        final int index = getIndex(segment);
        return index >= 0 && index < steps.size()
                ? steps.get(index)
                : null;
    }

    private static int getIndex(final Element segment) {
        final String index = segment.getAttribute(INDEX_ATTRIBUTE);
        return NullSafe.isBlankString(index)
                ? -1
                : Integer.parseInt(index);
    }

    private int getSelectedIndex() {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).element().equals(selected)) {
                return i;
            }
        }
        return -1;
    }

    // Whether a control's chevron is collapsed into a `…` chevron
    private static boolean isCollapsed(final Element control) {
        final Element segment = closest(control, "." + SEGMENT_CLASS);
        return segment != null && segment.hasClassName(COLLAPSED_CLASS);
    }

    private PipelineElement getSelected() {
        return getSelectionModel().getSelectedObject();
    }

    private List<PipelineElement> getChildren(final PipelineElement element) {
        return tree == null
                ? Collections.emptyList()
                : tree.getChildrenList(element);
    }

    private boolean isDisabled(final PipelineElement element) {
        return disabledElements.contains(element);
    }

    private boolean hasActiveFilters(final PipelineElement element) {
        return pipelineModel != null && pipelineModel.hasActiveFilters(element);
    }

    private PipelineElementType getElementType(final PipelineElement element) {
        return NullSafe.get(pipelineModel, model -> model.getElementType(element));
    }

    // The width an element takes in the row, i.e. its width less any overlap with the one before
    private static native int getLayoutWidth(Element element) /*-{
        var marginLeft = parseFloat($wnd.getComputedStyle(element).marginLeft) || 0;
        return Math.ceil(element.getBoundingClientRect().width + marginLeft);
    }-*/;

    private static native Element closest(Element element, String selector) /*-{
        return element && element.closest ? element.closest(selector) : null;
    }-*/;

    private static native Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;


    // --------------------------------------------------------------------------------


    /// Shows one of the bar's drop down menus, e.g. the branches at a fork.
    @FunctionalInterface
    public interface ShowMenu {

        /// Shows a menu.
        ///
        /// @param items         The menu's items.
        /// @param popupPosition Where to show the menu.
        /// @param opener        The element that opened the menu, which clicking doesn't close it.
        /// @param focusItem     The item to focus when the menu is shown, e.g. the current branch,
        ///                      or null for the first.
        /// @param onHide        Called when the menu is hidden.
        void show(List<Item> items,
                  PopupPosition popupPosition,
                  Element opener,
                  Item focusItem,
                  Runnable onHide);
    }


    interface Binder extends UiBinder<Widget, PipelinePathBar> {

    }
}
