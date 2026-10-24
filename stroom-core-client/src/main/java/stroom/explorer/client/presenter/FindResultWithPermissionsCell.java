/*
 * Copyright 2024 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.security.shared.DocumentUserPermissions;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Set;
import java.util.stream.Collectors;

public class FindResultWithPermissionsCell extends AbstractCell<FindResultWithPermissions> {

    private static final String BLOCK = "find-result-cell";

    @Override
    public void render(final Context context, final FindResultWithPermissions v, final SafeHtmlBuilder sb) {
        if (v != null) {
            final FindResult value = v.getFindResult();
            final DocumentUserPermissions permissions = v.getPermissions();
            if (value != null && value.getDocRef() != null) {
                final DocRef docRef = value.getDocRef();
                final SafeHtmlBuilder row = new SafeHtmlBuilder();
                final SafeHtmlBuilder main = new SafeHtmlBuilder();
                final SafeHtmlBuilder sub = new SafeHtmlBuilder();

                // Add icon
                final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
                if (documentType != null && documentType.getIcon() != null) {
                    main.append(SvgImageUtil.toSafeHtml(
                            docRef.getType(),
                            documentType.getIcon(),
                            BLOCK + "__icon",
                            "svgIcon"));
                }

                // Add name
                main.append(Templates.div(BLOCK + "__name",
                        SafeHtmlUtil.from(docRef.getName())));

                if (permissions != null) {
                    if (permissions.getPermission() != null) {
                        // Add permission
                        main.append(Templates.div(BLOCK + "__permission",
                                SafeHtmlUtil.from(permissions.getPermission().getDisplayValue())));
                    }
                    if (permissions.getInheritedPermission() != null) {
                        // Add inherited permission
                        main.append(Templates.div(BLOCK + "__inherited-permission",
                                SafeHtmlUtil.from("(" +
                                                  permissions.getInheritedPermission().getDisplayValue() +
                                                  ")")));
                    }
                }

                row.append(Templates.div(BLOCK + "__main", main.toSafeHtml()));

                // Add path
                sub.append(Templates.div(BLOCK + "__path",
                        SafeHtmlUtil.from(value.getPath())));

                // Add uuid
                sub.append(Templates.div(BLOCK + "__uuid",
                        SafeHtmlUtil.from(docRef.getUuid())));

                row.append(Templates.div(BLOCK + "__sub", sub.toSafeHtml()));

                if (permissions != null) {
                    addCreatePerms(row, permissions.getDocumentCreatePermissions());
                    addCreatePerms(row, permissions.getInheritedDocumentCreatePermissions());
                }

                sb.append(Templates.div(BLOCK, row.toSafeHtml()));
            }
        }
    }

    private void addCreatePerms(final SafeHtmlBuilder row, final Set<String> set) {
        if (set != null && set.size() > 0) {
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            sb.append(Templates.div(BLOCK + "__path",
                    SafeHtmlUtil.from(set.stream()
                            .collect(Collectors.joining(", ")))));
            row.append(Templates.div(BLOCK + "__sub", sb.toSafeHtml()));
        }
    }
}
