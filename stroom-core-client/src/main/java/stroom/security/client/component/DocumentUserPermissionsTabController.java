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

package stroom.security.client.component;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.security.client.DocPermissionRestClient;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// A document's permissions ([DocumentUserPermissionsTab]), as a tab of the document or a dialog:
/// the users and groups with permissions on it, which the user can edit, and the details of the
/// permissions of the one chosen.
public class DocumentUserPermissionsTabController extends Controller {

    private final DocumentUserPermissionsTab view;
    private final DocumentUserPermissionsListController documentUserPermissionsList;
    private final Provider<DocumentUserPermissionsEditController> documentUserPermissionsEditProvider;
    private final DocPermissionRestClient docPermissionClient;
    private final ButtonView docEdit;
    private DocRef docRef;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                            The application's event bus.
    /// @param view                                The content.
    /// @param docPermissionClient                 Fetches the chosen user or group's details.
    /// @param documentUserPermissionsList         The users and groups.
    /// @param documentUserPermissionsEditProvider Makes the dialog that edits one's permissions.
    @Inject
    public DocumentUserPermissionsTabController(
            final EventBus eventBus,
            final DocumentUserPermissionsTab view,
            final DocPermissionRestClient docPermissionClient,
            final DocumentUserPermissionsListController documentUserPermissionsList,
            final Provider<DocumentUserPermissionsEditController> documentUserPermissionsEditProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.documentUserPermissionsList = own(documentUserPermissionsList).attach(view.getList());
        this.documentUserPermissionsEditProvider = documentUserPermissionsEditProvider;
        this.docPermissionClient = docPermissionClient;

        documentUserPermissionsList.getToolbar().setName("Permission actions");
        docEdit = documentUserPermissionsList.getToolbar().button(
                SvgImage.EDIT,
                "Edit Permissions For Selected User",
                this::onEdit);
        docEdit.setEnabled(false);

        documentUserPermissionsList.setShowLevel(view.getPermissionVisibility());

        own(documentUserPermissionsList.getSelectionModel().addSelectionHandler(e -> {
            final DocumentUserPermissions selected =
                    documentUserPermissionsList.getSelectionModel().getSelected();
            updateDetails();
            docEdit.setEnabled(selected != null);
            if (e.getSelectionType().isDoubleSelect()) {
                onEdit();
            }
        }));
        view.setOnPermissionVisibilityChange(level -> {
            documentUserPermissionsList.setShowLevel(level);
            documentUserPermissionsList.refresh();
        });
    }

    /// Lists the permissions on a document.
    ///
    /// @param docRef The document.
    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        documentUserPermissionsList.setDocRef(docRef);
        documentUserPermissionsList.refresh();
    }

    private void onEdit() {
        final DocumentUserPermissions selected =
                documentUserPermissionsList.getSelectionModel().getSelected();
        if (selected != null) {
            documentUserPermissionsEditProvider.get().show(
                    this, docRef, selected, documentUserPermissionsList::refresh, taskMonitor());
        }
    }

    private void updateDetails() {
        final DocumentUserPermissions selection = documentUserPermissionsList
                .getSelectionModel()
                .getSelected();
        // Fetch detailed permissions report.
        if (selection != null) {
            docPermissionClient.getDocUserPermissionsReport(docRef, selection.getUserRef(), guard(response -> {
                final SafeHtml details = getDetails(response);
                view.setUserRef(selection.getUserRef());
                view.setDetails(details);
            }), taskMonitor());
        } else {
            view.setUserRef(null);
            view.setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
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
                                                    "icon-cell " + icon.getClassName()))
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

    // May return null
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
                                            "icon-cell " + icon.getClassName()))
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

}
