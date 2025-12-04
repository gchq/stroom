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

package stroom.explorer.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.explorer.client.event.ExplorerTaskMonitorFactory;
import stroom.explorer.client.event.ShowRemoveNodeTagsDialogEvent;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsProxy;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsView;
import stroom.explorer.shared.AddRemoveTagsRequest;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Presenter for removing tags from multiple nodes.
 * See {@link ExplorerNodeRemoveTagsPresenter} for tag removal on multiple nodes.
 */
public class ExplorerNodeRemoveTagsPresenter
        extends MyPresenter<ExplorerNodeRemoveTagsView, ExplorerNodeRemoveTagsProxy>
        implements ShowRemoveNodeTagsDialogEvent.Handler,
        HidePopupRequestEvent.Handler {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;

    private List<ExplorerNode> explorerNodes;

    @Inject
    public ExplorerNodeRemoveTagsPresenter(final EventBus eventBus,
                                           final RestFactory restFactory,
                                           final ExplorerNodeRemoveTagsView view,
                                           final ExplorerNodeRemoveTagsProxy proxy) {
        super(eventBus, view, proxy);
        this.restFactory = restFactory;
        view.setUiHandlers(new DefaultHideRequestUiHandlers(this));
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowRemoveNodeTagsDialogEvent event) {
//        GWT.log("onCreate: " + NullSafe.get(event.getExplorerNode(), ExplorerNode::toString));
        explorerNodes = NullSafe.list(event.getExplorerNodes());
        if (NullSafe.isEmptyCollection(explorerNodes)) {
            AlertEvent.fireError(this, "No explorer nodes supplied", null);
        } else {
            final List<DocRef> docRefs = explorerNodes.stream()
                    .map(ExplorerNode::getDocRef)
                    .collect(Collectors.toList());

            restFactory
                    .create(EXPLORER_RESOURCE)
                    .method(res -> res.fetchExplorerNodeTags(docRefs))
                    .onSuccess(nodetags -> {
                        getView().setData(docRefs, nodetags);
                        forceReveal();
                    })
                    .onFailure(t -> {
                        AlertEvent.fireError(
                                ExplorerNodeRemoveTagsPresenter.this,
                                t.getMessage(),
                                null);
                    })
                    .taskMonitorFactory(new ExplorerTaskMonitorFactory(this))
                    .exec();
        }
    }

    @Override
    protected void revealInParent() {
        final String caption = "Remove Tags from "
                               + NullSafe.size(explorerNodes)
                               + " Documents";

        final PopupSize popupSize = PopupSize.resizable(400, 500, 300, 300);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final Set<String> removeTags = getView().getRemoveTags();

            if (NullSafe.hasItems(removeTags)) {
                removeTagsFromNodes(e, removeTags);
            } else {
                e.hide();
            }
        } else {
            e.hide();
        }
    }

    private void removeTagsFromNodes(final HidePopupRequestEvent event,
                                     final Set<String> editedTags) {
        final List<DocRef> nodeDocRefs = getNodeDocRefs();
        restFactory
                .create(EXPLORER_RESOURCE)
                .call(res -> res.removeTags(new AddRemoveTagsRequest(nodeDocRefs, editedTags)))
                .onSuccess(voidResult -> {
                    // Update the node in the tree with the new tags
                    nodeDocRefs.forEach(docRef ->
                            RefreshDocumentEvent.fire(
                                    ExplorerNodeRemoveTagsPresenter.this, docRef));
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }


    private boolean isSingleDocRef() {
        return NullSafe.size(explorerNodes) == 1;
    }

    private ExplorerNode getSingleNode() {
        if (isSingleDocRef()) {
            return explorerNodes.get(0);
        } else {
            throw new RuntimeException("Expecting one node, found " + NullSafe.size(explorerNodes));
        }
    }

    private List<DocRef> getNodeDocRefs() {
        return NullSafe.stream(explorerNodes)
                .filter(Objects::nonNull)
                .map(ExplorerNode::getDocRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --------------------------------------------------------------------------------


    public interface ExplorerNodeRemoveTagsView
            extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        /**
         * @return The set of tags to remove from all nodes
         */
        Set<String> getRemoveTags();

        void setData(final List<DocRef> nodeDocRefs,
                     final Set<String> nodeTags);
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface ExplorerNodeRemoveTagsProxy extends Proxy<ExplorerNodeRemoveTagsPresenter> {

    }
}
