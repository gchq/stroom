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

package stroom.entity.client.view;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import stroom.entity.client.presenter.CopyEntityPresenter.CopyEntityView;
import stroom.entity.shared.PermissionInheritance;
import stroom.item.client.ItemListBox;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

public class CopyEntityViewImpl extends ViewWithUiHandlers<PopupUiHandlers>implements CopyEntityView {
    public interface Binder extends UiBinder<Widget, CopyEntityViewImpl> {
    }

    @UiField
    SimplePanel foldersOuter;
    @UiField
    SimplePanel foldersInner;
    @UiField
    TextBox name;
    @UiField
    ItemListBox<PermissionInheritance> permissionInheritance;

    private final Widget widget;

    @Inject
    public CopyEntityViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(new AttachEvent.Handler() {
            @Override
            public void onAttachOrDetach(final AttachEvent event) {
                Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                    @Override
                    public void execute() {
                        name.setFocus(true);
                    }
                });
            }
        });

        permissionInheritance.addItem(PermissionInheritance.NONE);
        permissionInheritance.addItem(PermissionInheritance.INHERIT);
        permissionInheritance.addItem(PermissionInheritance.COMBINED);
        permissionInheritance.setSelectedItem(PermissionInheritance.INHERIT);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void setFolderView(final View view) {
        view.asWidget().setWidth("100%");
        view.asWidget().setHeight("100%");
        foldersInner.setWidget(view.asWidget());
    }

//    @Override
//    public void setFoldersVisible(final boolean visible) {
//        foldersOuter.setVisible(visible);
//    }

    @Override
    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance.getSelectedItem();
    }

    @Override
    public void setPermissionInheritance(final PermissionInheritance permissionInheritance) {
        this.permissionInheritance.setSelectedItem(permissionInheritance);
    }

    @UiHandler("name")
    void onKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == '\r') {
            getUiHandlers().onHideRequest(false, true);
        }
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }
}
