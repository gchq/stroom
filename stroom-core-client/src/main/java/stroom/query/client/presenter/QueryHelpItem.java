package stroom.query.client.presenter;

import stroom.dashboard.shared.FunctionSignature;
import stroom.query.client.presenter.QueryHelpPresenter.InsertType;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.CompareUtil;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class QueryHelpItem implements Comparable<QueryHelpItem> {

    public static final Comparator<QueryHelpItem> CASE_INSENSITIVE_TITLE_COMPARATOR =
            CompareUtil.getNullSafeCaseInsensitiveComparator(QueryHelpItem::getTitle);

    static final String HELP_ITEM_BASE_CLASS = "queryHelpItem";
    static final String HELP_ITEM_LEAF_CLASS = HELP_ITEM_BASE_CLASS + "-leaf";
    static  final String HELP_ITEM_HEADING_CLASS = HELP_ITEM_BASE_CLASS + "-heading";
    static final int TOP_LEVEL_DEPTH = 0;

    final String title;
    final SvgImage icon;
    private final boolean heading;
    private final int depth;
    // Hold the path to this item, so we can use it to uniquely identify an item as some function names
    // exist in multiple categories
    protected String path;

    // child -> child map so we can get a common instance back
    private Map<QueryHelpItem, QueryHelpItem> children;

    public QueryHelpItem(final QueryHelpItem parent,
                         final String title,
                         final boolean heading) {
        this(parent, title, null, heading);
    }

    public QueryHelpItem(final QueryHelpItem parent,
                         final String title,
                         final SvgImage icon,
                         final boolean heading) {
        this.title = title;
        this.heading = heading;
        this.icon = icon;

        if (parent != null) {
            this.depth = parent.depth + 1;
            this.path = parent.path + "/" + title;
            // Ensure this item (or one equal to it) is included as a child on the parent
            parent.addOrGetChild(this);
        } else {
            this.depth = TOP_LEVEL_DEPTH;
            this.path = "/" + title;
        }
//        GWT.log("Creating: (depth " + this.depth + ") " + this.path);
    }

    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }

    public List<QueryHelpItem> getChildren() {
        if (children == null) {
            return null;
        }
        return children.keySet()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }

    public <T extends QueryHelpItem> T addOrGetChild(T child) {
        if (children == null) {
            children = new HashMap<>();
        }

        final QueryHelpItem child2 = children.computeIfAbsent(child, k -> child);

        return (T) child2;
    }

    public void clear() {
        children = null;
    }

    public String getInsertText() {
        return title;
    }

    public abstract InsertType getInsertType();

    public String getTitle() {
        return title;
    }

    public SvgImage getIcon() {
        return icon;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isHeading() {
        return heading;
    }

    String getClassName() {
        return HELP_ITEM_BASE_CLASS;
    }

    public SafeHtml getLabel() {
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<div class=\"" + getClassName() + "\">");
        builder.appendEscaped(title);
        builder.appendHtmlConstant("</div>");
        return builder.toSafeHtml();
    }

    public SafeHtml getDetail() {
        return SafeHtmlUtils.EMPTY_SAFE_HTML;
    }

    @Override
    public int compareTo(final QueryHelpItem o) {
        // Headings go first
        if (heading == o.heading) {
            return CASE_INSENSITIVE_TITLE_COMPARATOR.compare(this, o);
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
        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }


    // --------------------------------------------------------------------------------


    public abstract static class QueryHelpItemHeading extends QueryHelpItem {

        public QueryHelpItemHeading(final QueryHelpItem parent,
                                    final String text) {
            super(parent, text, true);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + HELP_ITEM_HEADING_CLASS;
        }

        @Override
        public InsertType getInsertType() {
            return InsertType.NOT_INSERTABLE;
        }
    }


    // --------------------------------------------------------------------------------


    public static class TopLevelHeadingItem extends QueryHelpItemHeading {

        private final SafeHtml detail;

        public TopLevelHeadingItem(final String text, final SafeHtml detail) {
            // Top level so no parent
            super(null, text);
            this.detail = detail;
        }

        @Override
        public SafeHtml getDetail() {
            return detail;
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + HELP_ITEM_BASE_CLASS + "-topLevel";
        }
    }


    // --------------------------------------------------------------------------------


    public abstract static class FunctionHeadingItem extends QueryHelpItemHeading {

        public FunctionHeadingItem(final QueryHelpItem parent,
                                   final String text) {
            super(parent, text);
        }
    }


    // --------------------------------------------------------------------------------


    public static class FunctionCategoryItem extends FunctionHeadingItem {

        public FunctionCategoryItem(final QueryHelpItem parent,
                                    final String heading) {
            super(parent, heading);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + HELP_ITEM_BASE_CLASS + "-functionCategory";
        }
    }


    // --------------------------------------------------------------------------------


    public static class OverloadedFunctionHeadingItem extends FunctionHeadingItem {

        public OverloadedFunctionHeadingItem(final QueryHelpItem parent,
                                             final String heading) {
            super(parent, heading);
        }

        @Override
        String getClassName() {
            return super.getClassName() + " " + HELP_ITEM_BASE_CLASS + "-functionHeading";
        }
    }


    // --------------------------------------------------------------------------------


    public static class FunctionItem extends QueryHelpItem {

        private final FunctionSignature signature;
        private final String helpUrl;
        private final String snippetText;

        public FunctionItem(final QueryHelpItem parent,
                            final String title,
                            final FunctionSignature signature,
                            final String helpUrl) {
            super(parent, title, false);
            this.signature = signature;
            this.helpUrl = helpUrl;
            this.snippetText = FunctionSignatureUtil.buildSnippetText(signature);
//            GWT.log("snippet: " + snippetText);
        }

        @Override
        public SafeHtml getDetail() {
            return FunctionSignatureUtil.buildInfoHtml(signature, helpUrl);
        }

        @Override
        String getClassName() {
            return super.getClassName()
                    + " " + HELP_ITEM_BASE_CLASS + "-function"
                    + " " + HELP_ITEM_LEAF_CLASS;
        }

        @Override
        public String getInsertText() {
            return snippetText;
        }

        @Override
        public InsertType getInsertType() {
            return GwtNullSafe.isBlankString(snippetText)
                    ? InsertType.BLANK
                    : InsertType.SNIPPET;
        }
    }
}
