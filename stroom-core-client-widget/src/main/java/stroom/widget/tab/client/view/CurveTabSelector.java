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

package stroom.widget.tab.client.view;

import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;

public class CurveTabSelector extends AbstractTabSelector {

    private static final Binder binder = GWT.create(Binder.class);

    @UiField
    DivElement arrows;
    @UiField
    DivElement text;

    public CurveTabSelector() {
        final DivElement element = binder.createAndBindUi(this);
        SvgImageUtil.setSvgAsInnerHtml(arrows, SvgImage.DOUBLE_ARROW);

        element.setAttribute("aria-label", "Tab Selector");
        setElement(element);
    }

    @Override
    protected void setKeyboardSelected(final boolean selected) {
        if (selected) {
            getElement().addClassName("curveTabSelector-keyboardSelected");
        } else {
            getElement().removeClassName("curveTabSelector-keyboardSelected");
        }
    }

    @Override
    protected void setHover(final boolean hover) {
        if (hover) {
            getElement().addClassName("curveTabSelector-hover");
        } else {
            getElement().removeClassName("curveTabSelector-hover");
        }
    }

    @Override
    public void setText(final String text) {
        this.text.setInnerText(text);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<DivElement, CurveTabSelector> {

    }
}
