package stroom.query.client.presenter;

import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpTitle;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.function.Consumer;

public class TitleDetailProvider implements DetailProvider {

    @Override
    public void getDetail(final QueryHelpRow row, final Consumer<Detail> consumer) {
        final QueryHelpTitle queryHelpTitle = (QueryHelpTitle) row.getData();
        final InsertType insertType = InsertType.NOT_INSERTABLE;
        final String insertText = row.getTitle();
        final SafeHtml documentation;
        if (GwtNullSafe.isBlankString(queryHelpTitle.getDocumentation())) {
            documentation = SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            documentation = SafeHtmlUtil.from(queryHelpTitle.getDocumentation());
        }
        final Detail detail = new Detail(insertType, insertText, documentation);
        consumer.accept(detail);
    }
}
