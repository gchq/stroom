package stroom.core.client;

import stroom.document.client.DocumentTabData;
import stroom.explorer.shared.FindResult;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class RecentItems {

    private final List<FindResult> recentItems = new ArrayList<>();

    public void add(final TabData tabData) {
        if (tabData instanceof DocumentTabData) {
            final DocumentTabData documentTabData = (DocumentTabData) tabData;
            final FindResult findResult = new FindResult(
                    documentTabData.getDocRef(),
                    documentTabData.getLabel(),
                    documentTabData.getIcon());
            recentItems.remove(findResult);
            recentItems.add(0, findResult);
        }
    }

    public List<FindResult> getRecentItems() {
        return recentItems;
    }
}
