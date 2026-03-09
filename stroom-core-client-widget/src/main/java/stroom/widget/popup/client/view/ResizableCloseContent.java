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
import stroom.widget.button.client.Button;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

public class ResizableCloseContent extends Composite implements DialogButtons {

    private static final Binder binder = GWT.create(Binder.class);
    private final HideRequestUiHandlers uiHandlers;

    @UiField
    Button close;
    @UiField
    SimplePanel content;

    public ResizableCloseContent(final HideRequestUiHandlers uiHandlers) {
        initWidget(binder.createAndBindUi(this));
        this.uiHandlers = uiHandlers;
        close.setIcon(SvgImage.CANCEL);
    }

    public void setContent(final Widget widget) {
        content.setWidget(widget);
    }

    @Override
    public void focus() {
        close.setFocus(true);
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        setEnabled(false);
        close.setLoading(true);
        uiHandlers.hideRequest(new HideRequest(action, () -> {
            setEnabled(true);
            close.setLoading(false);
        }));
    }

    @UiHandler("close")
    public void onCloseClick(final ClickEvent event) {
        onDialogAction(DialogAction.CLOSE);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        close.setEnabled(enabled);
    }

    public interface Binder extends UiBinder<Widget, ResizableCloseContent> {

    }
}
