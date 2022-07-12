package stroom.data.client.presenter;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;

import com.google.gwt.view.client.Range;

public final class CriteriaUtil {

    private CriteriaUtil() {
    }

    public static void setRange(final BaseCriteria criteria, final Range range) {
        final PageRequest pageRequest = criteria.obtainPageRequest();
        if (pageRequest != null) {
            pageRequest.setOffset(range.getStart());
            pageRequest.setLength(range.getLength());
        }
    }
}
