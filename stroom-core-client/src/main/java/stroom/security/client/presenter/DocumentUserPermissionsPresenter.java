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

package stroom.security.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter.DocumentUserPermissionsView;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.PermissionShowLevel;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocumentUserPermissionsPresenter
        extends ContentTabPresenter<DocumentUserPermissionsView> {

    private final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter;
    private final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider;
    private final DocPermissionRestClient docPermissionClient;
    private final ButtonView docEdit;
    private final SelectionBox<PermissionShowLevel> permissionVisibility;
    private DocRef docRef;

    @Inject
    public DocumentUserPermissionsPresenter(
            final EventBus eventBus,
            final DocPermissionRestClient docPermissionClient,
            final DocumentUserPermissionsView view,
            final DocumentUserPermissionsListPresenter documentUserPermissionsListPresenter,
            final Provider<DocumentUserPermissionsEditPresenter> documentUserPermissionsEditPresenterProvider) {

        super(eventBus, view);
        this.documentUserPermissionsListPresenter = documentUserPermissionsListPresenter;
        this.documentUserPermissionsEditPresenterProvider = documentUserPermissionsEditPresenterProvider;
        this.docPermissionClient = docPermissionClient;
        view.setDocUserPermissionListView(documentUserPermissionsListPresenter.getView());

        docEdit = documentUserPermissionsListPresenter.addButton(new Preset(
                SvgImage.EDIT,
                "Edit Permissions For Selected User",
                false));

        permissionVisibility = getView().getPermissionVisibility();
        permissionVisibility.addItems(PermissionShowLevel.ITEMS);
        permissionVisibility.setValue(PermissionShowLevel.SHOW_EXPLICIT);
        documentUserPermissionsListPresenter.setShowLevel(permissionVisibility.getValue());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(documentUserPermissionsListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final DocumentUserPermissions selected =
                    documentUserPermissionsListPresenter.getSelectionModel().getSelected();
            updateDetails();
            docEdit.setEnabled(selected != null);
            if (e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
        registerHandler(docEdit.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                onEdit();
            }
        }));
        registerHandler(permissionVisibility.addValueChangeHandler(e -> {
            documentUserPermissionsListPresenter.setShowLevel(permissionVisibility.getValue());
            documentUserPermissionsListPresenter.refresh();
        }));
    }

    private void onEdit() {
        final DocumentUserPermissions selected =
                documentUserPermissionsListPresenter.getSelectionModel().getSelected();
        if (selected != null) {
            documentUserPermissionsEditPresenterProvider.get().show(
                    docRef, selected, documentUserPermissionsListPresenter::refresh, this);
        }
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        documentUserPermissionsListPresenter.setDocRef(docRef);
        documentUserPermissionsListPresenter.refresh();
    }

//    public void show(final DocRef docRef) {
//        setDocRef(docRef);
//
//        final PopupSize popupSize = PopupSize.builder()
//                .width(Size
//                        .builder()
//                        .initial(1000)
//                        .min(1000)
//                        .resizable(true)
//                        .build())
//                .height(Size
//                        .builder()
//                        .initial(800)
//                        .min(800)
//                        .resizable(true)
//                        .build())
//                .build();
//
//        ShowPopupEvent.builder(this)
//                .popupType(PopupType.CLOSE_DIALOG)
//                .popupSize(popupSize)
//                .caption("Permissions For '" + docRef.getDisplayValue() + "'")
//                .modal()
//                .fire();
//    }

    private void updateDetails() {
        final DocumentUserPermissions selection = documentUserPermissionsListPresenter
                .getSelectionModel()
                .getSelected();
        // Fetch detailed permissions report.
        if (selection != null) {
            docPermissionClient.getDocUserPermissionsReport(docRef, selection.getUserRef(), response -> {
                final SafeHtml details = getDetails(response);
                getView().setUserRef(selection.getUserRef());
                getView().setDetails(details);
            }, this);
        } else {
            getView().setUserRef(null);
            getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
        }
    }

    private SafeHtml getDetails(final DocumentUserPermissionsReport permissions) {
        DocumentPermission maxPermission = null;

        DescriptionBuilder sb = new DescriptionBuilder();
        if (permissions.getExplicitPermission() != null) {
            sb.addTitle("Explicit Permission: " + toDisplayValue(permissions.getExplicitPermission()));
            maxPermission = permissions.getExplicitPermission();
        }

        if (NullSafe.hasEntries(permissions.getInheritedPermissionPaths())) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle("Inherited Permissions:");
            for (int i = DocumentPermission.LIST.size() - 1; i >= 0; i--) {
                final DocumentPermission permission = DocumentPermission.LIST.get(i);
                final List<String> paths = permissions.getInheritedPermissionPaths().get(permission);
                if (paths != null) {
                    if (maxPermission == null || permission.isHigher(maxPermission)) {
                        maxPermission = permission;
                    }

                    for (final String path : paths) {
                        sb.addNewLine();
                        sb.addLine(toDisplayValue(permission));
                        sb.addLine(" from ");
                        if (path.contains(">")) {
                            sb.addLine("ancestor group: ");
                        } else {
                            sb.addLine("parent group: ");
                        }

                        sb.addLine(path);
                    }
                }
            }

            final DescriptionBuilder sb2 = new DescriptionBuilder();
            sb2.addTitle("Effective Permission: "
                         + NullSafe.get(maxPermission, this::toDisplayValue));
            sb2.addNewLine();
            sb2.addNewLine();
            sb2.append(sb.toSafeHtml());
            sb = sb2;
        }

        final Set<String> explicitCreatePerms = permissions.getExplicitCreatePermissions();
        final SafeHtml explicitTypesHtml = docTypesToSortedDisplayList(explicitCreatePerms);

        if (explicitTypesHtml != null) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle("Explicit Create Document Permissions:");
            sb.append(explicitTypesHtml);
        }

        final Map<String, List<String>> inheritedCreatePermPaths = permissions.getInheritedCreatePermissionPaths();
        if (NullSafe.hasEntries(inheritedCreatePermPaths)) {
            sb.addNewLine();
            sb.addNewLine();
            sb.addTitle("Inherited Create Document Permissions:");

            final DescriptionBuilder finalSb = sb;
            inheritedCreatePermPaths.entrySet()
                    .stream()
                    .sorted(Entry.comparingByKey())
                    .forEach(entry -> {
                        final String type = entry.getKey();
                        final List<String> path = entry.getValue();
                        final DocumentType docType = DocumentTypeRegistry.get(type);
                        if (docType != null && NullSafe.hasItems(path)) {
                            final SvgImage icon = docType.getIcon();
                            final String pathStr = String.join(" & ", path);
                            Objects.requireNonNull(docType);
                            final HtmlBuilder htmlBuilder = HtmlBuilder.builder()
                                    .span(spanBuilder -> spanBuilder
                                                    .append(SafeHtmlUtils.fromTrustedString(icon.getSvg())),
                                            Attribute.title(docType.getDisplayType()),
                                            Attribute.className(
                                                    "svg-icon svgCell-icon " + icon.getClassName()))
                                    .italic(docType.getDisplayType())
                                    .append(" from: ")
                                    .append(pathStr);

                            finalSb.addNewLine();
                            finalSb.append(htmlBuilder.toSafeHtml());
                        }
                    });
        }

        if (maxPermission == null) {
            sb.addTitle("No Permission");
        }
        return sb.toSafeHtml();
    }

    /**
     * May return null
     */
    private SafeHtml docTypesToSortedDisplayList(final Set<String> types) {
        if (NullSafe.hasItems(types)) {
            //noinspection SimplifyStreamApiCallChains // Cos GWT
            final List<DocumentType> sortedDocTypes = DocumentTypeRegistry.getTypes()
                    .stream()
                    .filter(docType -> types.contains(docType.getType()))
                    .sorted(Comparator.comparing(DocumentType::getType))
                    .collect(Collectors.toList());
            if (NullSafe.hasItems(sortedDocTypes)) {
                final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
                for (int i = 0; i < sortedDocTypes.size(); i++) {
                    final DocumentType docType = sortedDocTypes.get(i);
                    final SvgImage icon = docType.getIcon();
                    htmlBuilder.br()
                            .span(spanBuilder -> spanBuilder
                                            .append(SafeHtmlUtils.fromTrustedString(icon.getSvg())),
                                    Attribute.title(docType.getDisplayType()),
                                    Attribute.className(
                                            "svg-icon svgCell-icon " + icon.getClassName()))
                            .italic(docType.getDisplayType());
                }
                return htmlBuilder.toSafeHtml();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }


    private String toDisplayValue(final DocumentPermission documentPermission) {
        return NullSafe.get(documentPermission,
                DocumentPermission::getDisplayValue,
                String::toUpperCase);
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.LOCKED;
    }

    @Override
    public String getLabel() {
        return "Permissions For '" + docRef.getDisplayValue() + "'";
    }

    @Override
    public String getType() {
        return "DocumentPermissions";
    }

    // --------------------------------------------------------------------------------


    public interface DocumentUserPermissionsView extends View {

        SelectionBox<PermissionShowLevel> getPermissionVisibility();

        void setDocUserPermissionListView(View view);

        void setDetails(SafeHtml details);

        void setUserRef(UserRef userRef);
    }
}
