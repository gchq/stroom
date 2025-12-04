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

package stroom.annotation.client;

import stroom.annotation.shared.Annotation;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Date;

public class FindAnnotationCell extends AbstractCell<Annotation> {

    private final DurationLabel durationLabel;

    public FindAnnotationCell(final DurationLabel durationLabel) {
        this.durationLabel = durationLabel;
    }

    @Override
    public void render(final Context context, final Annotation value, final SafeHtmlBuilder sb) {
        if (value != null && value.asDocRef() != null) {
            final HtmlBuilder htmlBuilder = new HtmlBuilder(sb);
            final DocRef docRef = value.asDocRef();
            htmlBuilder.div(row -> {
                // Add main text.
                row.div(main -> {
                    // Add icon
                    final DocumentType documentType = DocumentTypeRegistry.get(docRef.getType());
                    if (documentType != null && documentType.getIcon() != null) {
                        main.append(SvgImageUtil.toSafeHtml(
                                docRef.getType(),
                                documentType.getIcon(),
                                getCellClassName() + "-icon",
                                "svgIcon"));
                    }

                    // Add name
                    final String name = docRef.getName();
                    main.div(div -> div.append(name), Attribute.className(getCellClassName() + "-name"));

                    Lozenge.append(main, value.getStatus());
                    NullSafe.list(value.getLabels()).forEach(label -> Lozenge.append(main, label));
                    NullSafe.list(value.getCollections()).forEach(collection -> Lozenge.append(main, collection));

                }, Attribute.className(getCellClassName() + "-main"));

                // Add sub text.
                row.div(sub -> {
                    // Id
                    sub.div(div -> {
                        div.append('#');
                        div.append(value.getId());
                    });

                    // Dash
                    sub.div(div -> div.appendTrustedString("-"));

                    // User
                    sub.div(div -> div.append(value.getCreateUser()));

                    // Action
                    sub.div(div -> div.appendTrustedString("opened"));

                    // When
                    durationLabel.div(sub,
                            "annotationDurationLabel",
                            value.getCreateTimeMs(),
                            new Date());

                    if (value.getAssignedTo() != null) {
                        // Dash
                        sub.div(div -> div.appendTrustedString("-"));
                        // Assigned to
                        sub.div(div -> div.appendTrustedString("assigned to"));
                        sub.div(div -> div.append(value.getAssignedTo().toDisplayString()));
                    }

                }, Attribute.className(getCellClassName() + "-sub"));
            }, Attribute.className(getCellClassName() + "-row"));
        }
    }

    private String getCellClassName() {
        return "findAnnotationCell";
    }
}
