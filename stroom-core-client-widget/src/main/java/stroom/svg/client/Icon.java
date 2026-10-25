/*
 * Copyright 2026 Crown Copyright
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

package stroom.svg.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/// A picture: one of Stroom's icons, declared in a template, e.g. a tab's close cross:
///
/// ```
/// <svg:Icon image="TAB_CLOSE"/>
/// ```
///
/// It is the markup [stroom.widget.util.client.SvgImageUtil] writes, a `div` with the shared
/// `svg-image` classes holding the drawing, so it looks as an icon set in Java does. It is only a
/// picture, so screen readers skip it: what it is part of (a button, a tab) names itself.
public class Icon extends Widget {

    private SvgImage image;

    /// Makes an icon showing nothing, until [#setImage(SvgImage)] gives it its picture (in a
    /// template, its `image` attribute).
    public Icon() {
        final Element element = Document.get().createDivElement();
        element.setAttribute("aria-hidden", "true");
        setElement(element);
    }

    /// @param image The icon to show, or null for none.
    public void setImage(final SvgImage image) {
        if (this.image != null) {
            getElement().removeClassName(this.image.getClassName());
        }
        this.image = image;
        if (image == null) {
            getElement().removeClassName(SvgImage.BASE_CLASS_NAME);
            getElement().setInnerHTML("");
        } else {
            getElement().addClassName(SvgImage.BASE_CLASS_NAME);
            getElement().addClassName(image.getClassName());
            getElement().setInnerHTML(image.getSvg());
        }
    }

    /// @return The icon shown, or null for none.
    public SvgImage getImage() {
        return image;
    }
}
