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

package stroom.widget.popup.client.view;

import stroom.svg.shared.SvgImage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class SimplePopup extends AbstractPopupPanel {

    private static final Binder binder = GWT.create(Binder.class);

    @UiField
    SimplePanel content;

    /**
     * Creates an empty dialog box. It should not be shown until its child
     * widget has been added using {@link #add(Widget)}.
     */
    public SimplePopup(final DialogActionUiHandlers dialogEventHandler) {
        this(dialogEventHandler, true);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide <code>true</code> if the dialog should be automatically hidden
     *                 when the user clicks outside of it
     */
    public SimplePopup(final DialogActionUiHandlers dialogEventHandler, final boolean autoHide) {
        this(dialogEventHandler, autoHide, false);
    }

    /**
     * Creates an empty dialog box specifying its "auto-hide" property. It
     * should not be shown until its child widget has been added using
     * {@link #add(Widget)}.
     *
     * @param autoHide <code>true</code> if the dialog should be automatically hidden
     *                 when the user clicks outside of it
     * @param modal    <code>true</code> if keyboard and mouse events for widgets not
     *                 contained by the dialog should be ignored
     */
    public SimplePopup(final DialogActionUiHandlers dialogEventHandler, final boolean autoHide, final boolean modal) {
        super(dialogEventHandler, autoHide, modal);

        setStyleName("simplePopup-popup");
        setWidget(binder.createAndBindUi(this));
    }

    @Override
    public void setIcon(final SvgImage icon) {
        // No icon
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
     */
    @Override
    public void hide(final boolean autoClosed) {
        if (dialogActionHandler != null) {
            if (autoClosed) {
                dialogActionHandler.onDialogAction(DialogAction.AUTO_CLOSE);
            } else {
                dialogActionHandler.onDialogAction(DialogAction.CLOSE);
            }
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, SimplePopup> {

    }
}
