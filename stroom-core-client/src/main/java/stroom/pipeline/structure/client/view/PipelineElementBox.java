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

package stroom.pipeline.structure.client.view;

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class PipelineElementBox extends Box<PipelineElement> {

    private static final String BASE_CLASS = "pipelineElementBox";
    private static final String SELECTED_CLASS = BASE_CLASS + "-backgroundSelected";
    private static final String HOTSPOT_CLASS = BASE_CLASS + "-hotspot";
    private static final String REFUSED_CLASS = BASE_CLASS + "-refused";
    private static final String DETACHED_CLASS = BASE_CLASS + "-detached";
    private static final String DISABLED_CLASS = BASE_CLASS + "-disabled";
    private static final String SEVERITY_INFO_CLASS = BASE_CLASS + "-severityInfo";
    private static final String SEVERITY_WARN_CLASS = BASE_CLASS + "-severityWarn";
    private static final String SEVERITY_ERROR_CLASS = BASE_CLASS + "-severityError";
    private static final String SEVERITY_FATAL_ERROR_CLASS = BASE_CLASS + "-severityFatalError";
    private static final String BULLET = "•";
    private static final String PORT_CLASS = BASE_CLASS + "-port";
    private static final String PORT_FLOW_ATTRIBUTE = "data-flow";
    private static final String ROLE = "role";
    private static final String ROLE_IMG = "img";
    private static final String ARIA_LABEL = "aria-label";
    private static final String EMBEDDED_ICON_NAME = "Has embedded document";
    private static final String INHERITED_EMBEDDED_ICON_NAME = "Has embedded document inherited from parent pipeline";
    private static final String ACTIVE_FILTERS_NAME = "Has active stepping filters";

    private static final Map<String, String> severityNameToClassMap = new HashMap<>();

    static {
        severityNameToClassMap.put(Severity.INFO.name(), SEVERITY_INFO_CLASS);
        severityNameToClassMap.put(Severity.WARNING.name(), SEVERITY_WARN_CLASS);
        severityNameToClassMap.put(Severity.ERROR.name(), SEVERITY_ERROR_CLASS);
        severityNameToClassMap.put(Severity.FATAL_ERROR.name(), SEVERITY_FATAL_ERROR_CLASS);
    }

    private final PipelineModel pipelineModel;
    private PipelineElement pipelineElement;
    private final Widget filterIcon;
    private final Label label;
    // The dots over the box's left and right borders, where its connectors join it
    private final Element inPort;
    private final Element outPort;
    // The element's type, for its name as a tree item, e.g. "XSLT Filter"
    private final String typeName;
    private boolean detached;

    public PipelineElementBox(final PipelineModel pipelineModel,
                              final PipelineElement pipelineElement,
                              final SvgImage icon,
                              final String iconName) {
//        GWT.log("Creating pipe element " + pipelineElement.getId());
        this.pipelineModel = pipelineModel;
        this.pipelineElement = pipelineElement;
        this.typeName = iconName;

        final FlowPanel background = new FlowPanel();
        background.setStyleName(BASE_CLASS + "-background");

        label = new Label("", false);
        label.addStyleName(BASE_CLASS + "-label");
        setLabelText(pipelineElement.getDisplayName());

        label.getElement().setAttribute("title", pipelineElement.getDescription() != null
                ? pipelineElement.getDescription()
                : "");

        if (icon != null) {
            final SimplePanel image = new SimplePanel();
            SvgImageUtil.setSvgAsInnerHtml(
                    image,
                    icon,
                    "svgIcon",
                    BASE_CLASS + "-image");
            // Give the type icon a text alternative so a screen reader can say what type of
            // element this is
            nameImage(image, iconName);
            background.add(image);
        }

        background.add(label);

        final Optional<PipelineProperty> embeddedProperty = pipelineModel.getProperties(pipelineElement).stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getValue() != null && p.getValue().getEntity() != null &&
                        p.getValue().isEmbedded())
                .findAny();

        if (embeddedProperty.isPresent()) {
            final SimplePanel embeddedIcon = new SimplePanel();
            if (pipelineModel.getPipelineLayer().getPipelineData().getProperties() != null &&
                pipelineModel.getPipelineLayer().getPipelineData().getProperties().getAdd() != null &&
                pipelineModel.getPipelineLayer().getPipelineData().getProperties().getAdd().contains(
                        embeddedProperty.get())) {
                SvgImageUtil.setSvgAsInnerHtml(embeddedIcon, SvgImage.CODE, "svgIcon",
                        BASE_CLASS + " icon-colour__blue");
                nameImage(embeddedIcon, EMBEDDED_ICON_NAME);
            } else {
                SvgImageUtil.setSvgAsInnerHtml(embeddedIcon, SvgImage.EXPLORER, "svgIcon",
                        BASE_CLASS + " icon-colour__blue");
                nameImage(embeddedIcon, INHERITED_EMBEDDED_ICON_NAME);
            }
            background.add(embeddedIcon);
        }

        filterIcon = new SimplePanel();
        SvgImageUtil.setSvgAsInnerHtml(
                filterIcon,
                SvgImage.FILTER,
                "svgIcon",
                BASE_CLASS + "-filterImage icon-colour__green");

        background.add(filterIcon);
        updateFilterState();

        inPort = createPort(PORT_CLASS + "--in");
        outPort = createPort(PORT_CLASS + "--out");
        background.getElement().appendChild(inPort);
        background.getElement().appendChild(outPort);

        initWidget(background);
        updateName();
    }

    @Override
    public void setSelected(final boolean selected) {
        toggleClass(SELECTED_CLASS, selected);
        getElement().setAttribute("aria-selected", String.valueOf(selected));
    }

    /// Makes the box an item of the tree for assistive technology (`role="treeitem"`), named by its
    /// element's name and type, at its place in the tree, and whether it is the tree's one tab
    /// stop.
    ///
    /// @param level       Its depth in the tree, from 1 for the source.
    /// @param position    Its position among its siblings, from 1.
    /// @param count       How many siblings it has, itself included.
    /// @param hasChildren Whether it has children (which are always shown).
    /// @param tabStop     Whether it is the tree's tab stop.
    public void setTreeItem(final int level,
                            final int position,
                            final int count,
                            final boolean hasChildren,
                            final boolean tabStop) {
        final Element element = getElement();
        element.setAttribute("role", "treeitem");
        element.setAttribute("aria-level", String.valueOf(level));
        element.setAttribute("aria-posinset", String.valueOf(position));
        element.setAttribute("aria-setsize", String.valueOf(count));
        if (hasChildren) {
            element.setAttribute("aria-expanded", "true");
        } else {
            element.removeAttribute("aria-expanded");
        }
        element.setAttribute("tabindex", tabStop
                ? "0"
                : "-1");
        updateName();
    }

    /// Shows (or stops showing) that the element is detached, to be attached to another parent:
    /// faded, with a dashed border, and said so in its name.
    ///
    /// @param detached Whether it is detached.
    public void setDetached(final boolean detached) {
        this.detached = detached;
        toggleClass(DETACHED_CLASS, detached);
        updateName();
    }

    // e.g. "translation, XSLT Filter" or "rawWriter, XML Writer, detached"
    private void updateName() {
        if (getWidget() == null) {
            // Not built yet: the constructor names it once it is
            return;
        }
        final StringBuilder sb = new StringBuilder(NullSafe.string(label.getText()));
        if (NullSafe.isNonBlankString(typeName) && !typeName.equals(label.getText())) {
            sb.append(", ").append(typeName);
        }
        if (detached) {
            sb.append(", detached");
        }
        getElement().setAttribute("aria-label", sb.toString());
    }

    @Override
    public void showHotspot(final boolean show) {
        toggleClass(HOTSPOT_CLASS, show);
    }

    @Override
    public void showRefused(final boolean refused) {
        toggleClass(REFUSED_CLASS, refused);
    }

    public void setDisabled(final boolean disabled) {
        toggleClass(DISABLED_CLASS, disabled);
        // Tell assistive technology too, not only show it
        if (disabled) {
            getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
        } else {
            getElement().removeAttribute(DisabledState.ARIA_DISABLED);
        }
    }

    private void updateFilterState() {

        if (pipelineElement != null && pipelineModel != null && pipelineModel.hasActiveFilters(pipelineElement)) {
            filterIcon.addStyleName(BASE_CLASS + "-filterOn");
            filterIcon.removeStyleName(BASE_CLASS + "-filterOff");
            final String filterIconTitle = buildFilterIconTitle();
            filterIcon.setTitle(filterIconTitle);
            filterIcon.getElement().setAttribute(ROLE, ROLE_IMG);
            filterIcon.getElement().setAttribute(ARIA_LABEL, NullSafe.isBlankString(filterIconTitle)
                    ? ACTIVE_FILTERS_NAME
                    : filterIconTitle);
        } else {
            // Only the icon inside is hidden, so the empty holder mustn't be an image either
            filterIcon.addStyleName(BASE_CLASS + "-filterOff");
            filterIcon.removeStyleName(BASE_CLASS + "-filterOn");
            filterIcon.setTitle(null);
            filterIcon.getElement().removeAttribute(ROLE);
            filterIcon.getElement().removeAttribute(ARIA_LABEL);
        }
    }

    // Exposes an icon to assistive technology as an image with the given name, and shows the
    // name as a hover tip
    private static void nameImage(final Widget image, final String name) {
        if (!NullSafe.isBlankString(name)) {
            image.getElement().setAttribute(ROLE, ROLE_IMG);
            image.getElement().setAttribute(ARIA_LABEL, name);
            image.setTitle(name);
        }
    }

    private String buildFilterIconTitle() {
        if (pipelineElement == null || pipelineModel == null || pipelineModel.getStepFilterMap() == null) {
            return null;
        }

        final SteppingFilterSettings settings = pipelineModel.getStepFilterMap().get(pipelineElement.getId());
        if (settings == null) {
            return null;
        }

        final Severity skipToSeverity = settings.getSkipToSeverity();
        final OutputState skipToOutput = settings.getSkipToOutput();
        final List<XPathFilter> xPathFilters = NullSafe.list(settings.getFilters());
        if (skipToSeverity != null || skipToOutput != null || !xPathFilters.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Has active stepping filters:");

            if (skipToSeverity != null) {
                sb.append("\n  ")
                        .append(BULLET)
                        .append(" Jump to ")
                        .append(skipToSeverity);
            }

            if (skipToOutput != null) {
                final String outputStateStr = OutputState.EMPTY.equals(skipToOutput)
                        ? "empty"
                        : "non-empty";

                sb.append("\n  ")
                        .append(BULLET)
                        .append(" Jump to ")
                        .append(outputStateStr)
                        .append(" output");
            }
            final int xPathFilterCount = xPathFilters.size();
            if (xPathFilterCount == 1) {
                sb.append("\n  ")
                        .append(BULLET)
                        .append(" XPath filter");
            } else if (xPathFilterCount > 1) {
                sb.append("\n  ")
                        .append(BULLET)
                        .append(" ")
                        .append(xPathFilterCount)
                        .append(" XPath filters");
            }
            return sb.toString();
        }

        return null;
    }

    /// Shows the dots where the box's connectors join it, over its borders, coloured by what flows
    /// along them (see [PipelineConnectorRenderer#getFlow]): one on its left if it has a parent,
    /// and one on its right if it has children.
    ///
    /// @param hasChildren Whether the element has children in the tree shown.
    public void setPorts(final boolean hasChildren) {
        final PipelineElement parent = NullSafe.get(pipelineModel.getParentMap(), map -> map.get(pipelineElement));
        setPort(inPort, parent == null
                ? null
                : PipelineConnectorRenderer.getFlow(pipelineModel.getElementType(parent)));
        setPort(outPort, hasChildren
                ? PipelineConnectorRenderer.getFlow(pipelineModel.getElementType(pipelineElement))
                : null);
    }

    // A port dot, for sight only (the tree's structure is the connectors')
    private static Element createPort(final String className) {
        final Element port = Document.get().createSpanElement();
        port.setClassName(PORT_CLASS + " " + className);
        port.setAttribute("aria-hidden", "true");
        return port;
    }

    private static void setPort(final Element port, final PipelineConnectorRenderer.Flow flow) {
        port.setAttribute(PORT_FLOW_ATTRIBUTE, flow == null
                ? ""
                : flow.getName());
    }

    public void refresh(final PipelineElement pipelineElement) {
        this.pipelineElement = pipelineElement;
        setLabelText(pipelineElement.getName() != null
                ? pipelineElement.getName()
                : pipelineElement.getId());
        label.getElement().setAttribute("title", pipelineElement.getDescription() != null
                ? pipelineElement.getDescription()
                : "");
        updateFilterState();
    }

    // The element's name, also in data-text, so that the label keeps room for it in bold (see
    // PipelineElementBox.css)
    private void setLabelText(final String text) {
        label.setText(text);
        label.getElement().setAttribute("data-text", text == null
                ? ""
                : text);
        updateName();
    }

    private void toggleClass(final String className, final boolean isSet) {
        if (isSet) {
            getElement().addClassName(className);
        } else {
            getElement().removeClassName(className);
        }
    }

    @Override
    public PipelineElement getItem() {
        return pipelineElement;
    }

    /**
     * Set the severity for this pipe element or null if there isn't one.
     * Not all severities are rendered.
     */
    public void setSeverity(final Severity severity) {
        final String newClassName = severity != null
                ? severityNameToClassMap.get(severity.name())
                : null;
        final String classAttr = getElement().getClassName();
        for (final String severityClassName : severityNameToClassMap.values()) {
            if (!severityClassName.equals(newClassName) && classAttr.contains(severityClassName)) {
//                GWT.log("Un-setting " + this.pipelineElement.getId() + " from " + severity);
                getElement().removeClassName(severityClassName);
            }
        }
        if (newClassName != null && !classAttr.contains(newClassName)) {
//            GWT.log("Setting " + this.pipelineElement.getId() + " to " + severity);
            getElement().addClassName(newClassName);
        }
        setSeverityHoverTip(severity);
    }

    private void setSeverityHoverTip(final Severity severity) {
        final String namePart = pipelineElement.getType()
                                + " '" + pipelineElement.getId() + "'";
        if (severity == null) {
            getElement().setTitle(namePart);
        } else {
            String severityPart = severity.getSummaryValue().toLowerCase();
            if (Severity.INFO.equals(severity)) {
                severityPart += " messages";
            }
            getElement().setTitle(namePart + " has " + severityPart);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineElementBox that = (PipelineElementBox) o;
        return Objects.equals(pipelineElement, that.pipelineElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipelineElement);
    }
}
