/*
 * Copyright 2024 Crown Copyright
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

package stroom.widget.form.client;

import stroom.widget.button.client.Button;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeUri;
import com.google.gwt.safehtml.shared.annotations.IsSafeUri;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteHandler;
import com.google.gwt.user.client.ui.FormPanel.SubmitHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class CustomFileUpload extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);
    private final Widget widget;

    @UiField
    Button chooseFile;
    @UiField
    Label fileName;
    @UiField
    FormPanel form;
    @UiField
    FileUpload fileUpload;

    private CustomFileUpload() {
        widget = BINDER.createAndBindUi(this);
        form.setEncoding(FormPanel.ENCODING_MULTIPART);
        form.setMethod(FormPanel.METHOD_POST);
        fileUpload.setVisible(false);
        initWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public FormPanel getForm() {
        return form;
    }

    public String getFilename() {
        return fileUpload.getFilename();
    }

    public HandlerRegistration addSubmitCompleteHandler(final SubmitCompleteHandler handler) {
        return form.addSubmitCompleteHandler(handler);
    }

    public HandlerRegistration addSubmitHandler(final SubmitHandler handler) {
        return form.addSubmitHandler(handler);
    }

    public void reset() {
        form.reset();
    }

    public void setAction(@IsSafeUri final String url) {
        form.setAction(url);
    }

    public void setAction(final SafeUri url) {
        form.setAction(url);
    }

    public void setEncoding(final String encodingType) {
        form.setEncoding(encodingType);
    }

    public void setMethod(final String method) {
        form.setMethod(method);
    }

    public void submit() {
        form.submit();
    }

    public void focus() {
        chooseFile.setFocus(true);
    }

    @UiHandler("chooseFile")
    void onChooseFile(final ClickEvent e) {
        fileUpload.click();
    }

    @UiHandler("fileUpload")
    void onFileUpload(final ChangeEvent e) {
        String name = fileUpload.getFilename();
        if (name != null) {
            int index = name.lastIndexOf("/");
            if (index != -1) {
                name = name.substring(index + 1);
            }
            index = name.lastIndexOf("\\");
            if (index != -1) {
                name = name.substring(index + 1);
            }
        }
        fileName.setText(name);
    }

    public interface Binder extends UiBinder<Widget, CustomFileUpload> {

    }
}
