/*
 * Copyright 2017 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.RefreshDocumentEvent;
import stroom.explorer.client.event.ShowExplorerNodeTagsDialogEvent;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsProxy;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsView;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerResource;
import stroom.widget.popup.client.event.HidePopupEvent;
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

import java.util.Objects;
import java.util.Set;

public class ExplorerNodeEditTagsPresenter
        extends MyPresenter<ExplorerNodeEditTagsView, ExplorerNodeEditTagsProxy>
        implements ShowExplorerNodeTagsDialogEvent.Handler,
        HidePopupRequestEvent.Handler,
        HidePopupEvent.Handler {

    private static final ExplorerResource EXPLORER_RESOURCE = GWT.create(ExplorerResource.class);

    private final RestFactory restFactory;

    private ExplorerNode explorerNode;

    @Inject
    public ExplorerNodeEditTagsPresenter(final EventBus eventBus,
                                         final RestFactory restFactory,
                                         final ExplorerNodeEditTagsView view,
                                         final ExplorerNodeEditTagsProxy proxy) {
        super(eventBus, view, proxy);
        this.restFactory = restFactory;
        view.setUiHandlers(new DefaultHideRequestUiHandlers(this));
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowExplorerNodeTagsDialogEvent event) {
//        GWT.log("onCreate: " + GwtNullSafe.get(event.getExplorerNode(), ExplorerNode::toString));
        explorerNode = Objects.requireNonNull(event.getExplorerNode(),
                "Null explorerNode on ShowExplorerNodeTagsDialogEvent");
        final DocRef docRef = Objects.requireNonNull(explorerNode.getDocRef(), "Null docRef on explorerNode");

        final Rest<Set<String>> allNodeTagsRest = restFactory.create();
        allNodeTagsRest
                .onSuccess(allTags -> {

                    final Rest<ExplorerNode> expNodeRest = restFactory.create();
                    expNodeRest
                            .onSuccess(explorerNodeFromDb -> {
                                GWT.log("allTags: " + allTags);
                                GWT.log("nodeTags: " + explorerNodeFromDb.getTags());
                                getView().setData(docRef, explorerNodeFromDb.getTags(), allTags);
                                forceReveal();
                            })
                            .onFailure(this::handleFailure)
                            .call(EXPLORER_RESOURCE)
                            .getFromDocRef(explorerNode.getDocRef());
                })
                .onFailure(this::handleFailure)
                .call(EXPLORER_RESOURCE)
                .fetchExplorerNodeTags();
    }

    @Override
    protected void revealInParent() {
        final String caption = "Edit Tags for " + explorerNode.getName();

        final PopupSize popupSize = PopupSize.resizable(700, 700, 700, 700);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .onHide(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final Set<String> nodeTags = getView().getNodeTags();
            if (!Objects.equals(explorerNode.getTags(), nodeTags)) {

                final ExplorerNode updatedNode = explorerNode.copy()
                        .tags(nodeTags)
                        .build();


                final Rest<ExplorerNode> rest = restFactory.create();
                rest
                        .onSuccess(explorerNode -> {
                            // Update the node in the tree with the new tags
                            RefreshDocumentEvent.fire(
                                    ExplorerNodeEditTagsPresenter.this,
                                    explorerNode.getDocRef());
                            GWT.log("after update, tags: " + updatedNode.getTags());

                            e.hide();
                        })
                        .onFailure(this::handleFailure)
                        .call(EXPLORER_RESOURCE)
                        .updateNodeTags(updatedNode);
            }
        } else {
            e.hide();
        }
    }

    @Override
    public void onHide(final HidePopupEvent e) {

    }

    private void handleFailure(final Throwable t) {
        AlertEvent.fireError(
                ExplorerNodeEditTagsPresenter.this,
                t.getMessage(),
                null);
    }


    // --------------------------------------------------------------------------------


    public interface ExplorerNodeEditTagsView extends View, Focus, HasUiHandlers<HideRequestUiHandlers> {

        Set<String> getNodeTags();

        void setData(final DocRef nodeDocRef,
                     final Set<String> nodeTags,
                     final Set<String> allNodeTags);
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface ExplorerNodeEditTagsProxy extends Proxy<ExplorerNodeEditTagsPresenter> {

    }
}
