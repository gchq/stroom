/*
 * Copyright 2026 Crown Copyright
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

package stroom.explorer.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.shared.DecorateRequest;
import stroom.explorer.shared.ExplorerResource;
import stroom.explorer.shared.NodeFlag;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.Listeners;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// Lets the user choose a document with a picker ([DropDownBox]) it is attached to: it shows the
/// chosen document's name (checking it still exists, and its name, with the server), and opens the
/// explorer's chooser when the picker is asked to.
///
/// ```java
/// // A template declares the picker; its screen's controller attaches one of these to it
/// feedSelection = make(docSelectionControllerProvider);
/// feedSelection.attach(view.getFeedBox());
/// feedSelection.setIncludedTypes(FeedDoc.TYPE);
/// ```
public class DocSelectionController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    /// Shown when no document is chosen.
    public static final String NONE_DISPLAY_VALUE = "None";

    private final DocumentChooserController documentChooser;
    private final RestFactory restFactory;
    private final Listeners<DocRef> selectionListeners = new Listeners<>();
    private DropDownBox box;
    private TaskMonitorFactory taskMonitorFactory;
    private boolean enabled = true;
    private boolean readOnly;
    private DocRef value = null;
    private String errorMsg = null;
    private String itemType = null;
    // The perms required on any selected docRef or nodes in the picker
    private Set<DocumentPermission> requiredPermissions = null;

    /// Made by GIN, with the chooser it opens.
    ///
    /// @param eventBus      The application's event bus.
    /// @param documentChooser The chooser, which it shows again and again.
    /// @param restFactory   Checks the chosen document's name with the server.
    @Inject
    public DocSelectionController(final EventBus eventBus,
                                  final DocumentChooserController documentChooser,
                                  final RestFactory restFactory) {
        super(eventBus);
        this.documentChooser = own(documentChooser);
        this.restFactory = restFactory;
    }

    /// Attaches this to the picker it fills, and whose requests to open it answers.
    ///
    /// @param box The picker, e.g. one a template declares.
    public void attach(final DropDownBox box) {
        this.box = Objects.requireNonNull(box, "box");
        box.setOnOpen(this::showPopup);
        box.setEnabled(enabled);
        box.setReadOnly(readOnly);
        showValue();
    }

    /// @return The picker this is attached to, or null before [#attach(DropDownBox)].
    public DropDownBox getBox() {
        return box;
    }

    /// @param taskMonitorFactory What shows that the name is being checked, or null for the task
    ///                           monitor of what owns this ([Contexts#TASK_MONITOR]).
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    /// Puts the focus on the picker.
    public void focus() {
        if (box != null) {
            box.focus();
        }
    }

    /// Call this if the picker offers more than one type of document, e.g. all data source types.
    ///
    /// @param itemType What is chosen, singular and in sentence case, e.g. 'Data source'.
    public void setItemType(final String itemType) {
        this.itemType = itemType;
    }

    /// Sets the caption of the chooser the picker opens, by convention "Choose <Thing>" in title case.
    /// It defaults to "Choose item", which says nothing about what is being chosen.
    ///
    /// @param caption The caption; null leaves the default in place.
    public void setCaption(final String caption) {
        if (caption != null) {
            documentChooser.setCaption(caption);
        }
    }

    /// @param filterInput The chooser's quick filter when it opens.
    public void setQuickFilter(final String filterInput) {
        documentChooser.setInitialQuickFilter(filterInput);
    }

    /// @param includedTypes The types of document that may be chosen.
    public void setIncludedTypes(final String... includedTypes) {
        documentChooser.setIncludedTypes(includedTypes);
    }

    /// @param includedTypes The types of document that may be chosen.
    public void setIncludedTypes(final Collection<String> includedTypes) {
        documentChooser.setIncludedTypes(NullSafe.stream(includedTypes)
                .toArray(String[]::new));
    }

    /// @param tags The tags a document must have to be offered.
    public void setTags(final String... tags) {
        documentChooser.setTags(tags);
    }

    /// @param tags The tags a document must have to be offered.
    public void setTags(final Collection<String> tags) {
        documentChooser.setTags(NullSafe.stream(tags)
                .toArray(String[]::new));
    }

    /// @param tags The tags a document must have to be offered.
    public void setTags(final StandardExplorerTags... tags) {
        documentChooser.setTags(NullSafe.stream(tags)
                .map(StandardExplorerTags::getTagName)
                .toArray(String[]::new));
    }

    /// @param nodeFlags The flags of the explorer nodes offered.
    public void setNodeFlags(final NodeFlag... nodeFlags) {
        documentChooser.setNodeFlags(nodeFlags);
    }

    /// @param nodeFlags The flags of the explorer nodes offered.
    public void setNodeFlags(final Collection<NodeFlag> nodeFlags) {
        documentChooser.setNodeFlags(NullSafe.stream(nodeFlags)
                .toArray(NodeFlag[]::new));
    }

    /// @param requiredPermissions The permissions the user must have on the chosen document.
    public void setRequiredPermissions(final DocumentPermission... requiredPermissions) {
        this.requiredPermissions = NullSafe.asSet(requiredPermissions);
        documentChooser.setRequiredPermissions(requiredPermissions);

        // In case this is called after setSelectedEntityReference, call that again to
        // do the perm check now that we know what perms are needed.
        if (value != null) {
            setSelectedEntityReference(value, true, null, null);
        }
    }

    /// @return The permissions the user must have on the chosen document.
    public Set<DocumentPermission> getRequiredPermissions() {
        return requiredPermissions;
    }

    /// @return The chosen document, or null.
    public DocRef getSelectedEntityReference() {
        return value;
    }

    /// @param docRef         The document to show as chosen. If it doesn't exist or the user can't
    ///                       see it, a warning is shown but it is still the value.
    /// @param decorateDocRef If true, check with the server that it exists and has its current
    ///                       name. Set to false if you know docRef has an up-to-date name, is a
    ///                       constant, is definitely null, or you don't care.
    public void setSelectedEntityReference(final DocRef docRef, final boolean decorateDocRef) {
        setSelectedEntityReference(docRef, decorateDocRef, null, null);
    }

    /// As [#setSelectedEntityReference(DocRef, boolean, Consumer, Runnable)], with nothing to run if
    /// the document can't be found.
    ///
    /// @param docRef             The document to show as chosen.
    /// @param decorateDocRef     If true, check with the server that it exists and has its current name.
    /// @param onDocRefDecoration Given the document with its current name, if that differs.
    public void setSelectedEntityReference(final DocRef docRef,
                                           final boolean decorateDocRef,
                                           final Consumer<DocRef> onDocRefDecoration) {
        setSelectedEntityReference(docRef, decorateDocRef, onDocRefDecoration, null);
    }

    /// @param docRef             The document to show as chosen. If it doesn't exist or the user
    ///                           can't see it, a warning is shown but it is still the value.
    /// @param decorateDocRef     If true, check with the server that it exists and has its current
    ///                           name. Set to false if you know docRef has an up-to-date name, is a
    ///                           constant, is definitely null, or you don't care.
    /// @param onDocRefDecoration Given the document with its current name, if that differs.
    /// @param onDocRefNotFound   Run if the document doesn't exist, e.g. it has been deleted or its
    ///                           permissions have changed.
    public void setSelectedEntityReference(final DocRef docRef,
                                           final boolean decorateDocRef,
                                           final Consumer<DocRef> onDocRefDecoration,
                                           final Runnable onDocRefNotFound) {
        if (docRef != null && decorateDocRef) {
            // Selected as given at once, so that it is the value until decorating it is done, and if
            // that fails (rather than the value before, or none, which whatever reads the value,
            // e.g. a query expression's term editor, would save in its place)
            documentChooser.setSelectedEntityReference(docRef);
            setFieldValue(docRef, null);
            // The docRef may have come from the data within a Doc so the name may be out of date,
            // so decorate it to ensure we have the right name
            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(resource ->
                            resource.decorate(DecorateRequest.createWithPermCheck(
                                    docRef, getRequiredPermissions())))
                    .onSuccess(decoratedDocRef -> {
                        if (!Objects.equals(value, docRef)) {
                            // Another has been selected since, e.g. by a term editor moving on to
                            // another term: this one's reply is too late
                            return;
                        }
                        if (decoratedDocRef != null) {
                            documentChooser.setSelectedEntityReference(decoratedDocRef);
                            setFieldValue(decoratedDocRef, null);
                            if (!Objects.equals(docRef, decoratedDocRef)) {
                                // The decorated one is different so we need to update
                                if (onDocRefDecoration != null) {
                                    onDocRefDecoration.accept(decoratedDocRef);
                                }
                            }
                        } else {
                            handleDecorateError(docRef, onDocRefNotFound);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(getTaskMonitorFactory())
                    .exec();
        } else {
            // No decoration
            documentChooser.setSelectedEntityReference(docRef);
            setFieldValue(docRef, null);
        }
    }

    /// @param docRef The document that can't be found.
    /// @return What to tell the user, with the document's id and what to do, or null if there is none.
    public String buildNotFoundMessage(final DocRef docRef) {
        if (docRef != null) {
            final String type = Objects.requireNonNullElseGet(this.itemType, () ->
                    Objects.requireNonNullElse(docRef.getType(), "Document"));
            final String uuid = docRef.getUuid();
            final String displayName = NullSafe.getOrElse(
                    docRef.getName(),
                    name -> "'" + name + "' (" + uuid + ")",
                    uuid);

            return type +
                   " " +
                   displayName +
                   " doesn't exist or you do not have permission to view it.\n" +
                   "Select a different " + type.toLowerCase() + " or speak to your administrator.";
        } else {
            return null;
        }
    }

    /// @param allowFolderSelection Whether a folder may be chosen.
    public void setAllowFolderSelection(final boolean allowFolderSelection) {
        documentChooser.setAllowFolderSelection(allowFolderSelection);
    }

    /// Opens the chooser, unless the picker is disabled or read only.
    public void showPopup() {
        if (enabled && !readOnly) {
            final DocRef oldDocRef = getSelectedEntityReference();
            documentChooser.show(selectedDocRef -> {
                if (!Objects.equals(oldDocRef, selectedDocRef)) {
                    setFieldValue(selectedDocRef, null);
                    selectionListeners.fire(selectedDocRef);
                }
            });
        }
    }

    /// Listens for the user choosing a document, until the registration is removed.
    ///
    /// @param listener Told what is selected.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<DocRef> listener) {
        return selectionListeners.add(listener);
    }

    /// Makes the selection read only: it can be read and stays in the tab order, but the chooser
    /// doesn't open. It looks like the normal field with its value greyed.
    ///
    /// @param readOnly Whether the selection is read only.
    public void setReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        if (box != null) {
            box.setReadOnly(readOnly);
        }
    }

    /// @param enabled Whether the selection can be changed.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        if (box != null) {
            box.setEnabled(enabled);
        }
    }

    private TaskMonitorFactory getTaskMonitorFactory() {
        return taskMonitorFactory != null
                ? taskMonitorFactory
                : getProvidedTaskMonitor();
    }

    private void handleDecorateError(final DocRef docRef,
                                     final Runnable onDocRefNotFound) {
        // likely doesn't exist or user can't see it, so leave it as is
        // but show a warning, so they know there is a problem
        setFieldValue(docRef, buildNotFoundMessage(docRef));
        NullSafe.run(onDocRefNotFound);
    }

    // The not-found problem in brief, as shown under the picker; the full message (with the id and
    // what to do) is its tooltip and what screen readers read
    private String buildShortNotFoundMessage(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        final String name = NullSafe.getOrElse(docRef.getName(), n -> "'" + n + "'", "It");
        return name + " can't be found, or you don't have permission to see it.";
    }

    private void setFieldValue(final DocRef value, final String errorMsg) {
        this.value = value;
        this.errorMsg = errorMsg;
        showValue();
    }

    private void showValue() {
        if (box != null) {
            box.setText(NullSafe.getOrElse(value, DocRef::getDisplayValue, NONE_DISPLAY_VALUE));
            // A document that can't be found is shown as a problem with the field, under it
            if (NullSafe.isNonBlankString(errorMsg)) {
                box.setProblem(buildShortNotFoundMessage(value), errorMsg);
            } else {
                box.setProblem(null, null);
            }
        }
    }
}
