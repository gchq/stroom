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

package stroom.widget.tab.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.resources.client.ImageResource.ImageOptions;
import com.google.gwt.resources.client.ImageResource.RepeatStyle;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;

public class CurveTabSelector extends AbstractTabSelector {
    private static Binder binder = GWT.create(Binder.class);
    private static Resources resources = GWT.create(Resources.class);
    @UiField
    DivElement text;

    public CurveTabSelector() {
        final Style style = resources.style();
        style.ensureInjected();

        final DivElement element = binder.createAndBindUi(this);
        setElement(element);
    }

    @Override
    protected void setHover(final boolean hover) {
        if (hover) {
            getElement().addClassName(resources.style().hover());
        } else {
            getElement().removeClassName(resources.style().hover());
        }
    }

    @Override
    public void setText(final String text) {
        this.text.setInnerText(text);
    }

    interface Binder extends UiBinder<DivElement, CurveTabSelector> {
    }

    public interface Style extends CssResource {
        String DEFAULT_CSS = "CurveTabSelector.css";

        /**
         * Containers.
         */
        String curveTabSelector();

        /**
         * Backgrounds.
         */
        String background();

        String leftBackground();

        String midBackground();

        String rightBackground();

        /**
         * Over behaviour
         */
        String hover();

        /**
         * Content
         */
        String foreground();

        String arrows();

        String text();
    }

    public interface Resources extends ClientBundle {
        @Source("tabSelectorLeft.png")
        ImageResource left();

        @Source("tabSelectorMiddle.png")
        @ImageOptions(repeatStyle = RepeatStyle.Horizontal)
        ImageResource middle();

        @Source("tabSelectorRight.png")
        ImageResource right();

        @Source("arrows.png")
        ImageResource arrows();

        @Source(Style.DEFAULT_CSS)
        Style style();
    }
}
