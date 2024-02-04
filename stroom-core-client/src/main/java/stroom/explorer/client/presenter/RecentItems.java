package stroom.explorer.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.widget.tab.client.presenter.TabData;

import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class RecentItems {

    private final List<DocRef> recentItems = new ArrayList<>();

    public void add(final TabData tabData) {
        if (tabData instanceof DocumentTabData) {
            final DocumentTabData documentTabData = (DocumentTabData) tabData;
            recentItems.remove(documentTabData.getDocRef());
            recentItems.add(0, documentTabData.getDocRef());
        }
    }

    public List<DocRef> getRecentItems() {
        return recentItems;
    }
}
