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

package stroom.widget.popup.client.presenter;

import com.google.gwt.dom.client.Element;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public interface PopupView extends View, HasUiHandlers<PopupUiHandlers> {
    void setCaption(String caption);

    void setModal(boolean modal);

    void addAutoHidePartner(Element element);

    List<Element> getAutoHidePartners();

    void show(PopupType popupType);

    void show(PopupType popupType, int x, int y);

    void show(PopupType popupType, int x1, int x2, int y1, int y2);

    void hide(boolean autoClose);

    enum PopupType {
        POPUP, DIALOG, CLOSE_DIALOG, OK_CANCEL_DIALOG
    }
}
