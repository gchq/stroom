/*
 * Copyright 2016 Crown Copyright
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
import stroom.explorer.shared.PermissionInheritance;

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
    private final Chooser chooser;

    private ShowCreateDocumentDialogEvent(final String dialogCaption,
                                          final ExplorerNode selected,
                                          final String docType,
                                          final String initialDocName,
                                          final boolean allowNullFolder,
                                          final Consumer<ExplorerNode> newDocConsumer,
                                          final Chooser chooser) {
        this.dialogCaption = dialogCaption;
        this.selected = selected;
        this.docType = docType;
        this.initialDocName = initialDocName;
        this.allowNullFolder = allowNullFolder;
        this.newDocConsumer = newDocConsumer;
        this.chooser = chooser;
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
                        newDocConsumer,
                        null));
    }

    /// Shows the dialog only to choose a new document's name and folder, creating nothing: the choice is given to
    /// the chooser, e.g. to show the user what would be written before anything is.
    ///
    /// @param handlers       Where to fire it.
    /// @param dialogCaption  The dialog's caption.
    /// @param selected       The folder to suggest.
    /// @param docType        The type of document it is for.
    /// @param initialDocName The name to suggest.
    /// @param chooser        Given the name, folder and permission inheritance chosen, once the user clicks OK.
    public static void fireChoose(final HasHandlers handlers,
                                  final String dialogCaption,
                                  final ExplorerNode selected,
                                  final String docType,
                                  final String initialDocName,
                                  final Chooser chooser) {
        handlers.fireEvent(new ShowCreateDocumentDialogEvent(dialogCaption, selected, docType, initialDocName,
                false, null, chooser));
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

    /// @return Given the name and folder chosen, creating nothing, or null to create the document.
    public Chooser getChooser() {
        return chooser;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onCreate(final ShowCreateDocumentDialogEvent event);
    }


    // --------------------------------------------------------------------------------


    /// Given a new document's name and folder as the user chose them, when the dialog only chooses them.
    public interface Chooser {

        /// @param name                  The name chosen.
        /// @param folder                The folder chosen.
        /// @param permissionInheritance How the document is to take permissions from the folder.
        void chosen(String name, ExplorerNode folder, PermissionInheritance permissionInheritance);
    }
}
