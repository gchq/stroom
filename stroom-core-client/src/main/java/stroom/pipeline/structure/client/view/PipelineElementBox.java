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
import stroom.svg.client.Icon;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class PipelineElementBox extends Box<PipelineElement> {

    private final PipelineElement pipelineElement;

    public PipelineElementBox(final PipelineElement pipelineElement, final Icon icon) {
        this.pipelineElement = pipelineElement;

        final FlowPanel background = new FlowPanel();
        background.setStyleName("pipelineElementBox-background");

        final Label label = new Label(pipelineElement.getId(), false);
        label.addStyleName("pipelineElementBox-label");

        if (icon != null) {
            final Widget image = icon.asWidget();
            image.addStyleName("pipelineElementBox-image");
            background.add(image);
        }

        background.add(label);

        initWidget(background);
    }

    @Override
    public void setSelected(final boolean selected) {
        if (selected) {
            getElement().addClassName("pipelineElementBox-backgroundSelected");
        } else {
            getElement().removeClassName("pipelineElementBox-backgroundSelected");
        }
    }

    @Override
    public void showHotspot(final boolean show) {
        if (show) {
            getElement().addClassName("pipelineElementBox-hotspot");
        } else {
            getElement().removeClassName("pipelineElementBox-hotspot");
        }
    }

    @Override
    public PipelineElement getItem() {
        return pipelineElement;
    }
}
