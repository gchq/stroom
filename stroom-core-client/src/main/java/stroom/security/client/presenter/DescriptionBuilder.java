package stroom.security.client.presenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class DescriptionBuilder {

    private final SafeHtmlBuilder sb = new SafeHtmlBuilder();
    private boolean written;

    public void addLine(final String text) {
        addLine(false, false, text);
    }

    public void addLine(final boolean bold,
                        final boolean inherited,
                        final String text) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        if (inherited) {
            classNameBuilder.addClassName("inherited");
        }
        if (bold) {
            classNameBuilder.addClassName("bold");
        }

        sb.appendHtmlConstant("<span" + classNameBuilder.buildClassAttribute() + ">");
        sb.appendEscaped(text);
        sb.appendHtmlConstant("</span>");
        written = true;
    }

    public void addTitle(final String title) {
        addLine(true, false, title);
    }

    public void append(final SafeHtml safeHtml) {
        sb.append(safeHtml);
        written = true;
    }

    public void addNewLine() {
        if (written) {
            sb.appendHtmlConstant("<br/>");
        }
    }

    public SafeHtml toSafeHtml() {
        return sb.toSafeHtml();
    }
}
