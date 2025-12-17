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

public class ResizableAcceptRejectContent extends Composite implements DialogButtons {

    private static final Binder binder = GWT.create(Binder.class);

    public interface Binder extends UiBinder<Widget, ResizableAcceptRejectContent> {

    }

    private final HideRequestUiHandlers uiHandlers;

    @UiField
    Button accept;
    @UiField
    Button reject;
    @UiField
    SimplePanel content;

    public ResizableAcceptRejectContent(final HideRequestUiHandlers uiHandlers) {
        initWidget(binder.createAndBindUi(this));
        this.uiHandlers = uiHandlers;
        accept.setIcon(SvgImage.OK);
        reject.setIcon(SvgImage.CANCEL);
    }

    public void setContent(final Widget widget) {
        content.setWidget(widget);
        accept.setEnabled(true);
        reject.setEnabled(true);
    }

    @Override
    public void focus() {
        reject.setFocus(true);
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        setEnabled(false);
        if (action == DialogAction.OK) {
            accept.setLoading(true);
        } else {
            reject.setLoading(true);
        }
        uiHandlers.hideRequest(new HideRequest(action, () -> {
            setEnabled(true);
            accept.setLoading(false);
            reject.setLoading(false);
        }));
    }

    @UiHandler("accept")
    public void onAcceptClick(final ClickEvent event) {
        onDialogAction(DialogAction.OK);
    }

    @UiHandler("reject")
    public void onRejectClick(final ClickEvent event) {
        onDialogAction(DialogAction.CANCEL);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        accept.setEnabled(enabled);
        reject.setEnabled(enabled);
    }
}
