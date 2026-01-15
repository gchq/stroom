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

package stroom.data.client.view;

import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class ClassificationLabel extends Composite {

    private static final Binder binder = GWT.create(Binder.class);
    private final List<LabelColour> labelColours = new ArrayList<>();
    @UiField
    Label classification;

    @Inject
    public ClassificationLabel(final UiConfigCache clientPropertyCache) {
        initWidget(binder.createAndBindUi(this));

        clientPropertyCache.get(result -> {
            if (result != null) {
                final String csv = result.getTheme().getLabelColours();
                if (csv != null) {
                    final String[] parts = csv.split(",");
                    for (final String part : parts) {
                        final String[] kv = part.split("=");
                        if (kv.length == 2) {
                            final LabelColour labelColour = new LabelColour(kv[0].toUpperCase(), kv[1]);
                            labelColours.add(labelColour);
                        }
                    }
                }
            }
        }, new DefaultTaskMonitorFactory(this));
    }

    public void setClassification(final String text) {
        String upper = text;
        if (upper == null) {
            upper = "";
        }

        upper = upper.toUpperCase();
        classification.setText(upper);

        classification.getElement().getStyle().setColor("black");

        String colour = "#888888";
        for (final LabelColour labelColour : labelColours) {
            if (upper.indexOf(labelColour.getName()) != -1) {
                colour = labelColour.getColour();
                break;
            }
        }

        classification.getElement().getStyle().setBackgroundColor(colour);
    }

    public interface Binder extends UiBinder<Widget, ClassificationLabel> {

    }

    private static class LabelColour {

        private final String name;
        private final String colour;

        public LabelColour(final String name, final String colour) {
            this.name = name;
            this.colour = colour;
        }

        public String getName() {
            return name;
        }

        public String getColour() {
            return colour;
        }
    }
}
