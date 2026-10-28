/*
 * Copyright 2023 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.explorer.client.event.ExplorerTaskMonitorFactory;
import stroom.explorer.shared.AddRemoveTagsRequest;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Edit Tags ([ExplorerNodeEditTags]) for one document, or Add Tags for several, in a dialog made
/// for one use, shown once the tags known have been fetched.
public class ExplorerNodeEditTagsController extends Controller {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final ExplorerNodeEditTags view;
    private final RestFactory restFactory;
    private List<ExplorerNode> explorerNodes;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Fetches and saves the tags.
    @Inject
    public ExplorerNodeEditTagsController(final EventBus eventBus,
                                          final ExplorerNodeEditTags view,
                                          final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
    }

    /// Fetches the tags, then shows them in a dialog, owned by `opener` until it is closed. If they
    /// can't be fetched, or there are no documents, it says so and isn't shown.
    ///
    /// @param opener        What shows it.
    /// @param explorerNodes The documents.
    public void show(final HasControllerNode opener, final List<ExplorerNode> explorerNodes) {
        this.explorerNodes = NullSafe.list(explorerNodes);
        if (this.explorerNodes.isEmpty()) {
            fail("No explorer nodes supplied");
            return;
        }
        final List<DocRef> docRefs = this.explorerNodes.stream()
                .map(ExplorerNode::getDocRef)
                .collect(Collectors.toList());

        restFactory
                .create(EXPLORER_RESOURCE)
                .method(ExplorerResource::fetchExplorerNodeTags)
                .onSuccess(allTags -> {
                    if (isSingleDocRef()) {
                        restFactory
                                .create(EXPLORER_RESOURCE)
                                .method(res -> res.fetchExplorerNodeTags(docRefs))
                                .onSuccess(nodeTags -> {
                                    view.setData(docRefs, nodeTags, allTags);
                                    showDialog(opener);
                                })
                                .onFailure(this::handleFailure)
                                .taskMonitorFactory(new ExplorerTaskMonitorFactory(this))
                                .exec();
                    } else {
                        // Adding to multiple so don't need to know what tags the nodes have
                        view.setData(docRefs, Collections.emptySet(), allTags);
                        showDialog(opener);
                    }
                })
                .onFailure(this::handleFailure)
                .taskMonitorFactory(new ExplorerTaskMonitorFactory(this))
                .exec();
    }

    private void showDialog(final HasControllerNode opener) {
        final String nodeName = explorerNodes.size() > 1
                ? explorerNodes.size() + " Documents"
                : explorerNodes.get(0).getName();
        final String caption = isSingleDocRef()
                ? "Edit Tags on " + nodeName
                : "Add Tags to " + nodeName;

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizable(600, 600, 400, 400))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    private void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final Set<String> editedTags = view.getNodeTags();
            if (isSingleDocRef()) {
                if (!Objects.equals(explorerNodes.get(0).getTags(), editedTags)) {
                    updateTagsOnNode(e, editedTags);
                } else {
                    e.hide();
                }
            } else if (NullSafe.hasItems(editedTags)) {
                addTagsToNodes(e, editedTags);
            } else {
                e.hide();
            }
        } else {
            e.hide();
        }
    }

    private void addTagsToNodes(final HidePopupRequestEvent event, final Set<String> editedTags) {
        final List<DocRef> nodeDocRefs = getNodeDocRefs();
        restFactory
                .create(EXPLORER_RESOURCE)
                .call(res -> res.addTags(new AddRemoveTagsRequest(nodeDocRefs, editedTags)))
                .onSuccess(voidResult -> {
                    // Update the node in the tree with the new tags
                    nodeDocRefs.forEach(docRef -> RefreshDocumentEvent.fire(this, docRef));
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }

    private void updateTagsOnNode(final HidePopupRequestEvent event, final Set<String> editedTags) {
        final ExplorerNode updatedNode = explorerNodes.get(0).copy()
                .tags(editedTags)
                .build();
        restFactory
                .create(EXPLORER_RESOURCE)
                .method(res -> res.updateNodeTags(updatedNode))
                .onSuccess(explorerNode -> {
                    // Update the node in the tree with the new tags
                    RefreshDocumentEvent.fire(this, explorerNode.getDocRef());
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .owner(this)
                .exec();
    }

    // Not shown, so nothing owns this: it goes once the error is said
    private void handleFailure(final RestError t) {
        fail(t.getMessage());
    }

    private void fail(final String message) {
        AlertEvent.fireError(this, message, null);
        dispose();
    }

    private boolean isSingleDocRef() {
        return NullSafe.size(explorerNodes) == 1;
    }

    private List<DocRef> getNodeDocRefs() {
        return NullSafe.stream(explorerNodes)
                .filter(Objects::nonNull)
                .map(ExplorerNode::getDocRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
