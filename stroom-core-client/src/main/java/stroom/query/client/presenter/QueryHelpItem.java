package stroom.query.client.presenter;

import stroom.dashboard.shared.FunctionSignature;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class QueryHelpItem implements Comparable<QueryHelpItem> {

    final String text;
    private final boolean heading;
    private final int depth;

    private Map<QueryHelpItem, QueryHelpItem> children;

    public QueryHelpItem(final String text, final boolean heading, final int depth) {
        this.text = text;
        this.heading = heading;
        this.depth = depth;
    }

    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }

    public List<QueryHelpItem> getChildren() {
        if (children == null) {
            return null;
        }
        return children.keySet().stream().sorted().collect(Collectors.toList());
    }

    public <T extends QueryHelpItem> T addOrGetChild(T child) {
        if (children == null) {
            children = new HashMap<>();
        }

        return (T) children.computeIfAbsent(child, k -> child);
    }

    public void clear() {
        children = null;
    }

    public String getInsertText() {
        return text;
    }

    public int getDepth() {
        return depth;
    }

    String getClassName() {
        return "queryHelpItem";
    }

    public SafeHtml getLabel() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"" + getClassName() + "\">");
        builder.appendEscaped(text);
        builder.appendHtmlConstant("</div>");
        return builder.toSafeHtml();
    }

    public SafeHtml getDetail() {
        return SafeHtmlUtils.EMPTY_SAFE_HTML;
    }

    @Override
    public int compareTo(final QueryHelpItem o) {
        if (heading == o.heading) {
            return text.compareTo(o.text);
        }
        return Boolean.compare(o.heading, heading);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryHelpItem that = (QueryHelpItem) o;
        return heading == that.heading && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, heading);
    }

    public static class QueryHelpItemHeading extends QueryHelpItem {

        public QueryHelpItemHeading(final String text, final int depth) {
            super(text, true, depth);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + "queryHelpItem-heading";
        }
    }

    public static class FunctionHeadingItem extends QueryHelpItemHeading {

        public FunctionHeadingItem(final String text, final int depth) {
            super(text, depth);
        }
    }

    public static class FunctionCategoryItem extends FunctionHeadingItem {

        public FunctionCategoryItem(final String heading, final int depth) {
            super(heading, depth);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + "queryHelpItem-functionCategory";
        }
    }

    public static class FunctionItem extends QueryHelpItem {

        private final FunctionSignature signature;
        private final String helpUrl;

        public FunctionItem(final FunctionSignature signature, final String helpUrl, final int depth) {
            super(signature.getName(), false, depth);
            this.signature = signature;
            this.helpUrl = helpUrl;
        }

        @Override
        public SafeHtml getDetail() {
            return FunctionSignatureUtil.buildInfoHtml(signature, helpUrl);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + "queryHelpItem-function";
        }
    }
}
