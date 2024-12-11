package stroom.data.client.presenter;

import stroom.util.shared.PageRequest;

import com.google.gwt.view.client.Range;

public class PageRequestUtil {

    private PageRequestUtil() {
        // Util class.
    }

    public static PageRequest createPageRequest(final Range range) {
        return new PageRequest(range.getStart(), range.getLength());
    }
}
