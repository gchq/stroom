/*
 * Copyright 2022 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.query.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.query.shared.QueryDoc;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class QueryDocPresenter
        extends DocumentEditTabPresenter<LinkTabPanelView, QueryDoc> {

    private static final TabData QUERY_TAB = new TabDataImpl("Query");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");

    private final QueryDocEditPresenter queryEditPresenter;
    private final MarkdownEditPresenter markdownEditPresenter;

    @Inject
    public QueryDocPresenter(final EventBus eventBus,
                             final LinkTabPanelView view,
                             final QueryDocEditPresenter queryDocPresenter,
                             final MarkdownEditPresenter markdownEditPresenter) {
        super(eventBus, view);
        this.queryEditPresenter = queryDocPresenter;
        this.markdownEditPresenter = markdownEditPresenter;

        addTab(QUERY_TAB);
        addTab(DOCUMENTATION);
        selectTab(QUERY_TAB);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(queryEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        }));
    }

    @Override
    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (QUERY_TAB.equals(tab)) {
            callback.onReady(queryEditPresenter);
        } else if (DOCUMENTATION.equals(tab)) {
            callback.onReady(markdownEditPresenter);
        } else {
            callback.onReady(null);
        }
    }

    @Override
    public void onRead(final DocRef docRef, final QueryDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        queryEditPresenter.read(docRef, doc, readOnly);
        markdownEditPresenter.setText(doc.getDescription());
        markdownEditPresenter.setReadOnly(readOnly);
    }

    @Override
    protected QueryDoc onWrite(final QueryDoc doc) {
        QueryDoc modified = doc;
        modified = queryEditPresenter.write(modified);
        modified.setDescription(markdownEditPresenter.getText());
        return modified;
    }

    @Override
    public void onClose() {
        queryEditPresenter.onClose();
    }

    @Override
    public String getType() {
        return QueryDoc.DOCUMENT_TYPE;
    }
}
