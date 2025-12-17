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

import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.CustomRowStyle;
import stroom.query.api.TextAttributes;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CustomRowStylePresenter
        extends MyPresenterWidget<CustomRowStylePresenter.CustomRowStyleView>
        implements Focus {

    @Inject
    public CustomRowStylePresenter(final EventBus eventBus,
                                   final CustomRowStyleView view) {
        super(eventBus, view);
    }

    void show(final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Custom Row Style")
                .onShow(e -> getView().focus())
                .onHideRequest(handler)
                .fire();
    }

    void read(final CustomConditionalFormattingStyle customStyle,
              final TextAttributes textAttributes) {
        getView().setTextAttributes(textAttributes);
        if (customStyle != null) {
            if (customStyle.getLight() != null) {
                getView().setLightBackgroundColour(customStyle.getLight().getBackgroundColour());
                getView().setLightTextColour(customStyle.getLight().getTextColour());
            }
            if (customStyle.getDark() != null) {
                getView().setDarkBackgroundColour(customStyle.getDark().getBackgroundColour());
                getView().setDarkTextColour(customStyle.getDark().getTextColour());
            }
        }
    }

    CustomConditionalFormattingStyle write() {
        final CustomRowStyle light = CustomRowStyle.create(
                getView().getLightBackgroundColour(),
                getView().getLightTextColour());
        final CustomRowStyle dark = CustomRowStyle.create(
                getView().getDarkBackgroundColour(),
                getView().getDarkTextColour());
        if (light != null || dark != null) {
            return new CustomConditionalFormattingStyle(light, dark);
        }
        return null;
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public interface CustomRowStyleView extends View, Focus {

        String getLightBackgroundColour();

        void setLightBackgroundColour(String backgroundColour);

        String getLightTextColour();

        void setLightTextColour(String textColour);

        String getDarkBackgroundColour();

        void setDarkBackgroundColour(String backgroundColour);

        String getDarkTextColour();

        void setDarkTextColour(String textColour);

        void setTextAttributes(TextAttributes textAttributes);
    }
}
