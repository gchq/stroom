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
 */

package stroom.query.client.component;

import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.DocumentationTabController;
import stroom.entity.client.component.LinkTabPanel;
import stroom.query.shared.QueryDoc;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A query's editor: its Query, Documentation and Permissions tabs (see [DocumentEditor]). With
/// the Query tab showing, OK runs the query and Close stops it. A query embedded in a dashboard is
/// saved into the dashboard rather than on its own ([#setSaveInterceptor(Runnable)]).
public class QueryDocEditor extends DocumentEditor<QueryDoc> {

    private static final TabData QUERY = new TabDataImpl("Query");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final QueryTabController queryDocEdit;
    private Runnable saveInterceptor;

    /// Makes the editor, with its Query, Documentation and Permissions tabs.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 Its component.
    /// @param queryDocEdit         The query doc edit.
    /// @param documentationTabProvider Makes the Documentation tab's editor.
    /// @param permissionsProvider  Makes the Permissions tab.
    @Inject
    public QueryDocEditor(final EventBus eventBus,
                          final LinkTabPanel view,
                          final QueryTabController queryDocEdit,
                          final Provider<DocumentationTabController> documentationTabProvider,
                          final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        // Made up front, as the key actions reach it
        this.queryDocEdit = own(queryDocEdit);
        addOwnedTab(QUERY, queryDocEdit, QueryTabController::read, QueryTabController::write);
        addDocumentationTab(DOCUMENTATION,
                documentationTabProvider,
                QueryDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(QUERY);
    }

    /// @param saveInterceptor What to run in place of saving the query, e.g. to put it in the
    ///                        dashboard it is embedded in.
    public void setSaveInterceptor(final Runnable saveInterceptor) {
        this.saveInterceptor = saveInterceptor;
    }

    @Override
    public boolean handleKeyAction(final Action action) {
        if (Action.OK == action && QUERY.equals(getSelectedTab())) {
            queryDocEdit.start();
            return true;
        } else if (Action.CLOSE == action && QUERY.equals(getSelectedTab())) {
            queryDocEdit.stop();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onContentTabVisible(final boolean visible) {
        queryDocEdit.onContentTabVisible(visible);
    }

    @Override
    protected void beforeSave(final Runnable save) {
        if (saveInterceptor == null) {
            save.run();
        } else {
            saveInterceptor.run();
        }
    }
}
