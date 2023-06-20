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

import stroom.pipeline.shared.data.PipelineElement;
import stroom.svg.client.SvgImage;
import stroom.util.shared.Severity;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;

import java.util.HashMap;
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

    private static final Map<String, String> severityNameToClassMap = new HashMap<>();

    static {
        severityNameToClassMap.put(Severity.INFO.name(), SEVERITY_INFO_CLASS);
        severityNameToClassMap.put(Severity.WARNING.name(), SEVERITY_WARN_CLASS);
        severityNameToClassMap.put(Severity.ERROR.name(), SEVERITY_ERROR_CLASS);
        severityNameToClassMap.put(Severity.FATAL_ERROR.name(), SEVERITY_FATAL_ERROR_CLASS);
    }

    private final PipelineElement pipelineElement;

    public PipelineElementBox(final PipelineElement pipelineElement, final SvgImage icon) {
        GWT.log("Creating pipe element " + pipelineElement.getId());
        this.pipelineElement = pipelineElement;

        final FlowPanel background = new FlowPanel();
        background.setStyleName(BASE_CLASS + "-background");

        final Label label = new Label(pipelineElement.getId(), false);
        label.addStyleName(BASE_CLASS + "-label");

        if (icon != null) {
            final SimplePanel image = new SimplePanel();
            image.getElement().setInnerHTML(icon.getSvg());
            image.addStyleName("svgIcon " + BASE_CLASS + "-image");
            background.add(image);
        }

        background.add(label);

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
                GWT.log("Un-setting " + this.pipelineElement.getId() + " from " + severity);
                getElement().removeClassName(severityClassName);
            }
        }
        if (newClassName != null && !classAttr.contains(newClassName)) {
            GWT.log("Setting " + this.pipelineElement.getId() + " to " + severity);
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
