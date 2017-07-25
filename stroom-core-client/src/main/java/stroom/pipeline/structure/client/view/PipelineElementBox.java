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

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.svg.client.SvgIcon;

public class PipelineElementBox extends Box<PipelineElement> {
    private static Resources resources;
    private final PipelineElement pipelineElement;

    public PipelineElementBox(final PipelineElement pipelineElement, final SvgIcon icon) {
        this.pipelineElement = pipelineElement;

        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        final FlowPanel background = new FlowPanel();
        background.setStyleName(resources.style().background());

        final Label label = new Label(pipelineElement.getId(), false);
        label.addStyleName(resources.style().label());
        background.add(label);

        if (icon != null) {
            final Image image = new Image(icon.getUrl());
            image.addStyleName(resources.style().image());
            background.add(image);
            label.getElement().getStyle().setPaddingLeft(25, Unit.PX);
        }

        initWidget(background);
    }

    @Override
    public void setSelected(final boolean selected) {
        if (selected) {
            getElement().addClassName(resources.style().backgroundSelected());
        } else {
            getElement().removeClassName(resources.style().backgroundSelected());
        }
    }

    @Override
    public void showHotspot(final boolean show) {
        if (show) {
            getElement().addClassName(resources.style().hotspot());
        } else {
            getElement().removeClassName(resources.style().hotspot());
        }
    }

    @Override
    public PipelineElement getItem() {
        return pipelineElement;
    }

    public interface Style extends CssResource {
        String DEFAULT = "PipelineElementBox.css";

        String background();

        String backgroundSelected();

        String hotspot();

        String image();

        String label();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT)
        Style style();
    }
}
