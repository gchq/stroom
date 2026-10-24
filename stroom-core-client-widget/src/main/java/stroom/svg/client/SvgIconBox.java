/*
 * Copyright 2023 Crown Copyright
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
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

/// A field with an icon inside the end of its border that opens what the field chooses from, e.g. a
/// selection box's drop-down arrow or a date box's calendar. The icon is a picture: the field opens
/// the same thing from the keyboard (Enter), so the icon is hidden from screen readers and is no tab
/// stop, though clicking it opens it too. While the box is read only ([#setReadOnly(boolean)]) the
/// icon is dimmed and does nothing.
///
/// Styled by svg-icon-box.css: `svg-icon-box`, `svg-icon-box__field`, `svg-icon-box__icon`,
/// `svg-icon-box__image`, and the modifiers `svg-icon-box--drop-down` and `svg-icon-box--read-only`.
public class SvgIconBox extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String BLOCK = "svg-icon-box";
    /// The modifier of a box whose icon is dimmed and does nothing, as its field can't be changed.
    public static final String ICON_BOX_READONLY_CLASS_NAME = BLOCK + "--read-only";
    private static final String DROP_DOWN_CLASS_NAME = BLOCK + "--drop-down";

    private final HTMLPanel panel;
    @UiField
    SpanElement fieldSlot;
    @UiField
    DivElement icon;

    private Runnable onIconClick;

    public SvgIconBox() {
        panel = BINDER.createAndBindUi(this);
        initWidget(panel);
        addIconClickHandler(event -> {
            if (onIconClick != null) {
                onIconClick.run();
            }
        });
    }

    /// Puts a field in the box with an icon inside the end of its border.
    ///
    /// @param widget   The field, or a widget holding it (its first input is the field).
    /// @param svgImage The icon, e.g. [SvgImage#CALENDAR].
    public void setWidget(final Widget widget, final SvgImage svgImage) {
        panel.addAndReplaceElement(widget, fieldSlot);
        final Element field = findField(widget.getElement());
        if (field != null) {
            field.addClassName(BLOCK + "__field");
        }
        icon.setInnerHTML(svgImage.getSvg());
        // An SVG element's className isn't a string, so set the class attribute itself
        icon.getFirstChildElement().setAttribute("class", BLOCK + "__image");
    }

    /// Puts a field in the box with a drop-down arrow, the same arrow (and size and colour) as the
    /// value spinner's.
    ///
    /// @param widget The field, or a widget holding it (its first input is the field).
    public void setDropDownWidget(final Widget widget) {
        setWidget(widget, SvgImage.ARROW_DOWN);
        addStyleName(DROP_DOWN_CLASS_NAME);
    }

    /// @param onIconClick Run when the icon is clicked, unless the box is read only.
    public void setOnIconClick(final Runnable onIconClick) {
        this.onIconClick = onIconClick;
    }

    /// @param handler Called when the icon is clicked, unless the box is read only.
    /// @return The handler's registration.
    public HandlerRegistration addClickHandler(final ClickHandler handler) {
        return addIconClickHandler(handler);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        setStyleName(ICON_BOX_READONLY_CLASS_NAME, readOnly);
    }

    // Clicks on the icon (a plain element, so heard on the box)
    private HandlerRegistration addIconClickHandler(final ClickHandler handler) {
        return addDomHandler(event -> {
            final EventTarget target = event.getNativeEvent().getEventTarget();
            if (!isReadOnly() && Element.is(target) && icon.isOrHasChild(Element.as(target))) {
                handler.onClick(event);
            }
        }, ClickEvent.getType());
    }

    // The widget's element if it is an input, else the first input in it
    private static Element findField(final Element root) {
        if ("input".equalsIgnoreCase(root.getTagName())) {
            return root;
        }
        final NodeList<Element> inputs = root.getElementsByTagName("input");
        return inputs.getLength() == 0
                ? null
                : inputs.getItem(0);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<HTMLPanel, SvgIconBox> {

    }
}
