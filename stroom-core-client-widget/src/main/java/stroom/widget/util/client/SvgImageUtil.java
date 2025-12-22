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

package stroom.widget.util.client;

import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.UIObject;

import java.util.Objects;

public class SvgImageUtil {

    private SvgImageUtil() {
    }

    public static SafeHtml toSafeHtml(final Preset preset, final String... classNames) {
        Objects.requireNonNull(preset);
        return toSafeHtml(preset.getTitle(), preset.getSvgImage(), classNames);
    }

    public static SafeHtml toSafeHtml(final SvgImage svgImage,
                                      final String... classNames) {
        return toSafeHtml(null, svgImage, classNames);
    }

    public static SafeHtml toSafeHtml(final String title,
                                      final SvgImage svgImage,
                                      final String... classNames) {
        Objects.requireNonNull(svgImage);
        final String allClassNames = SvgImage.BASE_CLASS_NAME + " "
                                     + svgImage.getClassName() + " "
                                     + NullSafe.join(" ", classNames);
        final SafeHtml svgHtml = SafeHtmlUtil.getSafeHtmlFromSafeConstant(svgImage.getSvg());

        //        GWT.log("safeHtml: " + safeHtml.asString());
        return NullSafe.isBlankString(title)
                ? Templates.div(allClassNames, svgHtml)
                : Templates.div(allClassNames, title, svgHtml);
    }

    /**
     * Utility method to render an SVG image from SVG characters.
     * Assumes that the character sequence is safe.
     * @param title The title of the image. Can be null or empty.
     * @param svgData The character sequence holding the SVG data.
     * @param classNames Any CSS classnames associated with the image.
     * @return The character sequence to render.
     */
    public static SafeHtml toSafeHtml(final String title,
                                      final String svgData,
                                      final String... classNames) {
        final String allClassNames = SvgImage.BASE_CLASS_NAME + " "
                + NullSafe.join(" ", classNames);
        final SafeHtml svgHtml = SafeHtmlUtil.getSafeHtmlFromSafeConstant(svgData);

        return NullSafe.isBlankString(title)
                ? Templates.div(allClassNames, svgHtml)
                : Templates.div(allClassNames, title, svgHtml);
    }

    public static SafeHtml emptySvg(final String... classNames) {
        return Templates.emptySvg(NullSafe.join(" ", classNames));
    }

    public static void setSvgAsInnerHtml(final Element element,
                                         final Preset preset,
                                         final String... classNames) {
        if (element != null) {
            final SafeHtml safeHtml = toSafeHtml(preset, classNames);
            element.setInnerSafeHtml(safeHtml);
        }
    }

    public static void setSvgAsInnerHtml(final UIObject uiObject,
                                         final SvgImage svgImage,
                                         final String... classNames) {
        if (uiObject != null) {
            final SafeHtml safeHtml = toSafeHtml(svgImage, classNames);
            uiObject.getElement().setInnerSafeHtml(safeHtml);
        }
    }

    public static void setSvgAsInnerHtml(final Element element,
                                         final SvgImage svgImage,
                                         final String... classNames) {
        if (element != null) {
            final SafeHtml safeHtml = toSafeHtml(svgImage, classNames);
            element.setInnerSafeHtml(safeHtml);
        }
    }

    public static void setSvgAsInnerHtml(final UIObject uiObject,
                                         final String title,
                                         final SvgImage svgImage,
                                         final String... classNames) {
        if (uiObject != null) {
            final SafeHtml safeHtml = toSafeHtml(title, svgImage, classNames);
            uiObject.getElement().setInnerSafeHtml(safeHtml);
        }
    }

    public static void setSvgAsInnerHtml(final Element element,
                                         final String title,
                                         final SvgImage svgImage,
                                         final String... classNames) {
        if (element != null) {
            final SafeHtml safeHtml = toSafeHtml(title, svgImage, classNames);
            element.setInnerSafeHtml(safeHtml);
        }
    }
}
