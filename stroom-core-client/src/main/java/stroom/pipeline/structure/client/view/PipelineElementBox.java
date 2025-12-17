/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PipelineElementBox extends Box<PipelineElement> {

    private static final String BASE_CLASS = "pipelineElementBox";
    private static final String SELECTED_CLASS = BASE_CLASS + "-backgroundSelected";
    private static final String HOTSPOT_CLASS = BASE_CLASS + "-hotspot";
    private static final String SEVERITY_INFO_CLASS = BASE_CLASS + "-severityInfo";
    private static final String SEVERITY_WARN_CLASS = BASE_CLASS + "-severityWarn";
    private static final String SEVERITY_ERROR_CLASS = BASE_CLASS + "-severityError";
    private static final String SEVERITY_FATAL_ERROR_CLASS = BASE_CLASS + "-severityFatalError";
    private static final String BULLET = "•";

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

    public PipelineElementBox(final PipelineModel pipelineModel,
                              final PipelineElement pipelineElement,
                              final SvgImage icon) {
//        GWT.log("Creating pipe element " + pipelineElement.getId());
        this.pipelineModel = pipelineModel;
        this.pipelineElement = pipelineElement;

        final FlowPanel background = new FlowPanel();
        background.setStyleName(BASE_CLASS + "-background");

        final String labelText = pipelineElement.getDisplayName();
        label = new Label(labelText, false);
        label.addStyleName(BASE_CLASS + "-label");

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
            background.add(image);
        }

        background.add(label);

        filterIcon = new SimplePanel();
        SvgImageUtil.setSvgAsInnerHtml(
                filterIcon,
                SvgImage.FILTER,
                "svgIcon",
                BASE_CLASS + "-filterImage icon-colour__green");

        background.add(filterIcon);
        updateFilterState();

        initWidget(background);
    }

    @Override
    public void setSelected(final boolean selected) {
        toggleClass(SELECTED_CLASS, selected);
    }

    @Override
    public void showHotspot(final boolean show) {
        toggleClass(HOTSPOT_CLASS, show);
    }

    private void updateFilterState() {

        if (pipelineElement != null && pipelineModel != null && pipelineModel.hasActiveFilters(pipelineElement)) {
            filterIcon.addStyleName(BASE_CLASS + "-filterOn");
            filterIcon.removeStyleName(BASE_CLASS + "-filterOff");
            filterIcon.setTitle(buildFilterIconTitle());
        } else {
            filterIcon.addStyleName(BASE_CLASS + "-filterOff");
            filterIcon.removeStyleName(BASE_CLASS + "-filterOn");
            filterIcon.setTitle(null);
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

    public void refresh(final PipelineElement pipelineElement) {
        this.pipelineElement = pipelineElement;
        label.setText(pipelineElement.getName() != null
                ? pipelineElement.getName()
                : pipelineElement.getId());
        label.getElement().setAttribute("title", pipelineElement.getDescription() != null
                ? pipelineElement.getDescription()
                : "");
        updateFilterState();
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
