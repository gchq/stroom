package stroom.data.client.presenter;

import stroom.util.shared.BaseCriteria;

import com.google.gwt.view.client.Range;

public final class CriteriaUtil {

    private CriteriaUtil() {
    }

    public static void setRange(final BaseCriteria criteria, final Range range) {
        criteria.setPageRequest(PageRequestUtil.createPageRequest(range));
    }
}
