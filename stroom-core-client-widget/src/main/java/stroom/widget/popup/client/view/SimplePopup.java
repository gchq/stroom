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

package stroom.widget.popup.client.view;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class SimplePopup extends AbstractPopupPanel {
    private static Binder binder = GWT.create(Binder.class);

    public interface Binder extends UiBinder<Widget, SimplePopup> {
    }

    public interface Style extends CssResource {
        String DEFAULT_STYLE = "SimplePopup.css";

        String popup();

        String container();

        String background();

        String content();
    }

    public interface Resources extends ClientBundle {
        @Source(Style.DEFAULT_STYLE)
        Style style();
    }

    private static final Resources RESOURCES = GWT.create(Resources.class);

    private final PopupUiHandlers popupUiHandlers;

    @UiField
    SimplePanel content;

    /**
     * Creates an empty dialog box. It should not be shown until its child
     * widget has been added using {@link #add(Widget)}.
     */
    public SimplePopup(final PopupUiHandlers popupUiHandlers) {
        this(popupUiHandlers, true);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide
     *            <code>true</code> if the dialog should be automatically hidden
     *            when the user clicks outside of it
     */
    public SimplePopup(final PopupUiHandlers popupUiHandlers, final boolean autoHide) {
        this(popupUiHandlers, autoHide, false);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide
     *            <code>true</code> if the dialog should be automatically hidden
     *            when the user clicks outside of it
     * @param modal
     *            <code>true</code> if keyboard and mouse events for widgets not
     *            contained by the dialog should be ignored
     */
    public SimplePopup(final PopupUiHandlers popupUiHandlers, final boolean autoHide, final boolean modal) {
        super(autoHide, modal);
        RESOURCES.style().ensureInjected();
        this.popupUiHandlers = popupUiHandlers;

        setStyleName(RESOURCES.style().popup());
        setWidget(binder.createAndBindUi(this));
    }

    @Override
    public void setCaption(final String text) {
        // N/A
    }

    @Override
    public void setContent(final Widget widget) {
        content.setWidget(widget);
    }

    @Override
    public void forceHide(final boolean autoClosed) {
        super.hide(autoClosed);
    }

    /**
     * This is overridden as we don't want popups to hide without the presenter
     * to get a chance to do something about it. When hide occurs the presenter
     * will be notified via the event and will then choose whether or not to
     * force the popup to hide.
     *
     * @param autoClosed
     * @see com.google.gwt.user.client.ui.PopupPanel#hide(boolean)
     */
    @Override
    public void hide(final boolean autoClosed) {
        popupUiHandlers.onHideRequest(autoClosed, false);
    }
}
