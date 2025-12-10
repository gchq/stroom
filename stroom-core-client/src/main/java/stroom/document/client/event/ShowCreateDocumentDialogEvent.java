/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.document.client.event;

import stroom.explorer.shared.ExplorerNode;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.function.Consumer;

public class ShowCreateDocumentDialogEvent extends GwtEvent<ShowCreateDocumentDialogEvent.Handler> {

    private static Type<Handler> TYPE;

    private final String dialogCaption;
    private final ExplorerNode selected;
    private final String docType;
    private final String initialDocName;
    private final boolean allowNullFolder;
    private final Consumer<ExplorerNode> newDocConsumer;

    private ShowCreateDocumentDialogEvent(final String dialogCaption,
                                          final ExplorerNode selected,
                                          final String docType,
                                          final String initialDocName,
                                          final boolean allowNullFolder,
                                          final Consumer<ExplorerNode> newDocConsumer) {
        this.dialogCaption = dialogCaption;
        this.selected = selected;
        this.docType = docType;
        this.initialDocName = initialDocName;
        this.allowNullFolder = allowNullFolder;
        this.newDocConsumer = newDocConsumer;
    }

    public static void fire(final HasHandlers handlers,
                            final String dialogCaption,
                            final ExplorerNode selected,
                            final String docType,
                            final String initialDocName,
                            final boolean allowNullFolder,
                            final Consumer<ExplorerNode> newDocConsumer) {
        handlers.fireEvent(
                new ShowCreateDocumentDialogEvent(
                        dialogCaption,
                        selected,
                        docType,
                        initialDocName,
                        allowNullFolder,
                        newDocConsumer));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onCreate(this);
    }

    public String getDialogCaption() {
        return dialogCaption;
    }

    public ExplorerNode getSelected() {
        return selected;
    }

    public String getDocType() {
        return docType;
    }

    public String getInitialDocName() {
        return initialDocName;
    }

    public boolean isAllowNullFolder() {
        return allowNullFolder;
    }

    public Consumer<ExplorerNode> getNewDocConsumer() {
        return newDocConsumer;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCreate(final ShowCreateDocumentDialogEvent event);
    }
}
