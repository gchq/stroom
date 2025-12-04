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

package stroom.svg.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class SvgIconBox extends FlowPanel {

    public static final String ICON_BOX_READONLY_CLASS_NAME = "svgIconBox-readonly";
    private SimplePanel outer;
    private SimplePanel inner;
    private boolean isReadonly = false;

    public SvgIconBox() {
        setStyleName("svgIconBox");
    }

    public void setWidget(final Widget widget, final SvgImage svgImage) {
        this.add(widget);

        inner = new SimplePanel();
        inner.getElement().setInnerHTML(svgImage.getSvg());
        inner.getElement().setClassName("svgIconBox-icon-inner icon-colour__grey svgIcon " + svgImage.getClassName());

        outer = new SimplePanel(inner);
        outer.getElement().setClassName("svgIconBox-icon-outer");

        this.add(outer);
    }

    public HandlerRegistration addClickHandler(final ClickHandler handler) {
        return outer.addDomHandler(event -> {
            if (!isReadonly()) {
                handler.onClick(event);
            }
        }, ClickEvent.getType());
    }

    public void setReadonly(final boolean isReadonly) {
        this.isReadonly = isReadonly;
        if (isReadonly) {
            inner.getElement().addClassName(ICON_BOX_READONLY_CLASS_NAME);
        } else {
            inner.getElement().removeClassName(ICON_BOX_READONLY_CLASS_NAME);
        }
    }

    private boolean isReadonly() {
        return isReadonly;
    }
}
