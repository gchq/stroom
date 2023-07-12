package stroom.widget.util.client;

import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.UIObject;

import java.util.Objects;

public class SvgImageUtil {

    private static final Template TEMPLATE = GWT.create(Template.class);

    private SvgImageUtil() {
    }

    public static SafeHtml toSafeHtml(final Preset preset) {
        Objects.requireNonNull(preset);
        return toSafeHtml(preset.getTitle(), preset.getSvgImage());
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
                + GwtNullSafe.join(" ", classNames);
        final SafeHtml svgHtml = SafeHtmlUtil.getSafeHtmlFromSafeConstant(svgImage.getSvg());

        final SafeHtml safeHtml = GwtNullSafe.isBlankString(title)
                ? TEMPLATE.icon(allClassNames, svgHtml)
                : TEMPLATE.icon(allClassNames, title, svgHtml);
//        GWT.log("safeHtml: " + safeHtml.asString());
        return safeHtml;
    }

    public static SafeHtml emptySvg(final String... classNames) {
        return TEMPLATE.emptySvg(GwtNullSafe.join(" ", classNames));
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


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\"><svg></svg></div>")
        SafeHtml emptySvg(String className);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml icon(String className, SafeHtml icon);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml icon(String className, String title, SafeHtml icon);
    }
}
