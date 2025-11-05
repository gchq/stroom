package stroom.widget.util.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;

public class Templates {

    private static final Template TEMPLATE = GWT.create(Template.class);


    public static SafeHtml div(final String className) {
        return TEMPLATE.div(className);
    }

    public static SafeHtml div(final String className, final SafeHtml content) {
        return TEMPLATE.div(className, content);
    }

    public static SafeHtml divWithTitle(final String title, final SafeHtml content) {
        return TEMPLATE.divWithTitle(title, content);
    }

    public static SafeHtml div(final String className, final String title, final SafeHtml content) {
        return TEMPLATE.div(className, title, content);
    }

    public static SafeHtml emptySvg(final String className) {
        return TEMPLATE.emptySvg(className);
    }

    public static SafeHtml input(final String value) {
        return TEMPLATE.input(value);
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\"></div>")
        SafeHtml div(String className);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithTitle(String title, SafeHtml content);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml div(String className, String title, SafeHtml content);

        @Template("<div class=\"{0}\"><svg></svg></div>")
        SafeHtml emptySvg(String className);

        @Template("<input type=\"text\" value=\"{0}\"></input>")
        SafeHtml input(String value);
    }
}
