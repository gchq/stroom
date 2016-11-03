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

package stroom.widget.button.client;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.MyCustomButton;

public class ImageButton extends MyCustomButton implements ImageButtonView {
    private static final String DEFAULT_STYLE = "imageButton";

    public ImageButton() {
        setStyleName(DEFAULT_STYLE);
    }

    @Override
    public void setEnabledImage(final ImageResource enabledImage) {
        if (enabledImage != null) {
            super.getUpFace().setImage(new Image(enabledImage));
        }
    }

    @Override
    public void setDisabledImage(final ImageResource disabledImage) {
        if (disabledImage != null) {
            getUpDisabledFace().setImage(new Image(disabledImage));
        }
    }

    @Override
    protected void onClickStart() {
        getElement().addClassName("imageButton-down");
        super.onClickStart();
    }

    @Override
    protected void onClickCancel() {
        super.onClickCancel();
        getElement().removeClassName("imageButton-down");
    }

    @Override
    protected void onClick() {
        super.onClick();
        getElement().removeClassName("imageButton-down");
        removeStyleName("imageButton-up-hovering");
    }

    public void hide(final boolean hide) {
        final NodeList<Element> images = getElement().getElementsByTagName("img");
        if (images.getLength() > 0) {
            final Element img = images.getItem(0);
            if (hide) {
                img.getStyle().setVisibility(Visibility.HIDDEN);
            } else {
                img.getStyle().setVisibility(Visibility.VISIBLE);
            }
        }
    }
}
