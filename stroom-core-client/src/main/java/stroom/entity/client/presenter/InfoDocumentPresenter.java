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

package stroom.entity.client.presenter;

import stroom.data.client.presenter.CopyTextUtil;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeInfo;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserName;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.Set;
import java.util.stream.Collectors;

public class InfoDocumentPresenter
        extends MyPresenter<InfoDocumentPresenter.InfoDocumentView, InfoDocumentPresenter.InfoDocumentProxy>
        implements ShowInfoDocumentDialogEvent.Handler {

    private final DateTimeFormatter dateTimeFormatter;
    private boolean isShowing = false;

    @Inject
    public InfoDocumentPresenter(final EventBus eventBus,
                                 final InfoDocumentView view,
                                 final InfoDocumentProxy proxy,
                                 final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view, proxy);
        this.dateTimeFormatter = dateTimeFormatter;
    }

    @Override
    protected void onBind() {
        registerHandler(getView().asWidget().addDomHandler(e ->
                CopyTextUtil.onClick(e.getNativeEvent()), MouseDownEvent.getType()));
    }

    @Override
    protected void revealInParent() {
        // Popup is not modal so, allow the user to click Info for another doc
        // which will just update the view and not need a new show event.
        if (!isShowing) {
            final PopupSize popupSize = PopupSize.resizable(500, 500);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(popupSize)
                    .caption("Info")
                    .onShow(e -> {
                        getView().focus();
                        isShowing = true;
                    })
                    .onHide(e -> isShowing = false)
                    .fire();
        }
    }

    @ProxyEvent
    @Override
    public void onCreate(final ShowInfoDocumentDialogEvent event) {
        final ExplorerNodeInfo explorerNodeInfo = event.getExplorerNodeInfo();
        final DocRefInfo info = event.getDocRefInfo();
        final DocRef docRef = info.getDocRef();
        final ExplorerNode explorerNode = event.getExplorerNode();

        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        if (info.getOtherInfo() != null) {
            final int index = info.getOtherInfo().indexOf(":");
            if (index != -1) {
                appendLine(info.getOtherInfo().substring(0, index),
                        info.getOtherInfo().substring(index + 1),
                        sb);
            }
        }

        appendLine("UUID", docRef.getUuid(), sb);
        appendLine("Type", docRef.getType(), sb);
        appendLine("Name", docRef.getName(), sb);

        if (GwtNullSafe.hasItems(explorerNodeInfo.getOwners())) {
            final Set<UserName> owners = explorerNodeInfo.getOwners();
            if (owners.size() > 1) {
                appendLine("Owners", explorerNodeInfo.getOwners()
                        .stream()
                        .map(UserName::getUserIdentityForAudit)
                        .collect(Collectors.joining(", ")), sb);
            } else {
                appendLine("Owner", explorerNodeInfo.getOwners()
                        .stream()
                        .map(UserName::getUserIdentityForAudit)
                        .collect(Collectors.joining(", ")), sb);
            }
        }
        if (info.getCreateUser() != null) {
            appendLine("Created By", info.getCreateUser(), sb);
        }
        if (info.getCreateTime() != null) {
            appendLine("Created On", dateTimeFormatter.format(info.getCreateTime()), sb);
        }
        if (info.getUpdateUser() != null) {
            appendLine("Updated By", info.getUpdateUser(), sb);
        }
        if (info.getUpdateTime() != null) {
            appendLine("Updated On", dateTimeFormatter.format(info.getUpdateTime()), sb);
        }
        if (GwtNullSafe.hasItems(explorerNode.getTags())) {
//            final SafeHtmlBuilder sbInner = new SafeHtmlBuilder();
            appendLine("Tags", "", sb);
//            appendKey("Tags", sbInner);

            final Set<String> tags = explorerNode.getTags();
            tags.stream()
                    .sorted()
                    .forEach(tag ->
                            appendLine("\t", tag, sb));
//            sb.append(CopyTextUtil.div("infoLine", sbInner.toSafeHtml()));
        }

        getView().setInfo(sb.toSafeHtml());

        forceReveal();
    }

    private void appendLine(final String key, final String value, final SafeHtmlBuilder sb) {
        final SafeHtmlBuilder sbInner = new SafeHtmlBuilder();
        appendKey(key, sbInner);
        sbInner.append(CopyTextUtil.render(value));
        sb.append(CopyTextUtil.div("infoLine", sbInner.toSafeHtml()));
    }

    private void appendKey(final String key, final SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<b>");
        sb.appendEscaped(key);
        sb.appendHtmlConstant("</b>");
        if (!GwtNullSafe.isBlankString(key)) {
            sb.appendEscaped(": ");
        }
    }


    // --------------------------------------------------------------------------------


    public interface InfoDocumentView extends View, Focus {

        void setInfo(SafeHtml info);
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface InfoDocumentProxy extends Proxy<InfoDocumentPresenter> {

    }
}
