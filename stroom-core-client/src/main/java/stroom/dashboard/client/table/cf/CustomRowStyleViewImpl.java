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

package stroom.dashboard.client.table.cf;

import stroom.query.api.TextAttributes;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class CustomRowStyleViewImpl extends ViewImpl implements CustomRowStylePresenter.CustomRowStyleView {

    private final Widget widget;

    @UiField
    TextBox lightBackgroundColour;
    @UiField
    TextBox lightTextColour;
    @UiField
    SimplePanel lightExample;

    @UiField
    TextBox darkBackgroundColour;
    @UiField
    TextBox darkTextColour;
    @UiField
    SimplePanel darkExample;

    private TextAttributes textAttributes;

    @Inject
    public CustomRowStyleViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        updateLightSwatch();
        updateDarkSwatch();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getLightBackgroundColour() {
        return lightBackgroundColour.getValue();
    }

    @Override
    public void setLightBackgroundColour(final String backgroundColour) {
        this.lightBackgroundColour.setValue(backgroundColour);
        updateLightSwatch();
    }

    @Override
    public String getLightTextColour() {
        return lightTextColour.getValue();
    }

    @Override
    public void setLightTextColour(final String textColour) {
        this.lightTextColour.setValue(textColour);
        updateLightSwatch();
    }

    @Override
    public String getDarkBackgroundColour() {
        return darkBackgroundColour.getValue();
    }

    @Override
    public void setDarkBackgroundColour(final String backgroundColour) {
        this.darkBackgroundColour.setValue(backgroundColour);
        updateDarkSwatch();
    }

    @Override
    public String getDarkTextColour() {
        return darkTextColour.getValue();
    }

    @Override
    public void setDarkTextColour(final String textColour) {
        this.darkTextColour.setValue(textColour);
        updateDarkSwatch();
    }

    @Override
    public void setTextAttributes(final TextAttributes textAttributes) {
        this.textAttributes = textAttributes;
    }

    @Override
    public void focus() {
        lightBackgroundColour.setFocus(true);
    }

    private void updateLightSwatch() {
        lightExample.getElement().setInnerHTML(
                ConditionalFormattingSwatchUtil
                        .createCustomSwatch(
                                lightBackgroundColour.getValue(),
                                lightTextColour.getValue(),
                                textAttributes)
                        .asString());
    }

    private void updateDarkSwatch() {
        darkExample.getElement().setInnerHTML(
                ConditionalFormattingSwatchUtil
                        .createCustomSwatch(
                                darkBackgroundColour.getValue(),
                                darkTextColour.getValue(),
                                textAttributes)
                        .asString());
    }

    @UiHandler("lightBackgroundColour")
    public void onLightBackgroundColour(final ValueChangeEvent<String> e) {
        updateLightSwatch();
    }

    @UiHandler("lightTextColour")
    public void onLightTextColour(final ValueChangeEvent<String> e) {
        updateLightSwatch();
    }

    @UiHandler("darkBackgroundColour")
    public void onDarkBackgroundColour(final ValueChangeEvent<String> e) {
        updateDarkSwatch();
    }

    @UiHandler("darkTextColour")
    public void onDarkTextColour(final ValueChangeEvent<String> e) {
        updateDarkSwatch();
    }

    public interface Binder extends UiBinder<Widget, CustomRowStyleViewImpl> {

    }
}
