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

package stroom.pipeline.client.component;

import stroom.entity.client.component.CodeTabController;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.MarkdownEditController;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// A text converter's editor: its Conversion (the converter's code), Settings, Documentation and
/// Permissions tabs (see [DocumentEditor]). The code is highlighted as the converter's type is
/// when it is read.
public class TextConverterEditor extends DocumentEditor<TextConverterDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData CONVERSION = new TabDataImpl("Conversion");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    /// Makes the editor, with its Conversion, Settings, Documentation and Permissions tabs.
    @Inject
    public TextConverterEditor(final EventBus eventBus,
                               final LinkTabPanel view,
                               final Provider<TextConverterSettingsController> settingsProvider,
                               final Provider<CodeTabController> codeTabProvider,
                               final Provider<MarkdownEditController> markdownEditProvider,
                               final Provider<DocumentUserPermissionsTabController> permissionsProvider) {
        super(eventBus, view);
        addTab(CONVERSION,
                () -> {
                    final CodeTabController conversion = codeTabProvider.get();
                    conversion.setOnChange(this::onChange);
                    return conversion;
                },
                (conversion, docRef, doc) -> {
                    conversion.setText(doc.getData());
                    conversion.setMode(editorMode(doc.getConverterType(), conversion.getText()));
                },
                (conversion, doc) -> doc.copy().data(conversion.getText()).build());
        addTab(SETTINGS,
                settingsProvider::get,
                TextConverterSettingsController::read,
                TextConverterSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                TextConverterDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        setDefaultTab(CONVERSION);
    }

    /// The language a converter's code is highlighted as: its type's, or, if it has none, the one
    /// its code looks like.
    ///
    /// @param converterType The converter's type, or null.
    /// @param code          The converter's code, or null.
    /// @return The language.
    static AceEditorMode editorMode(final TextConverterType converterType, final String code) {
        if (converterType != null && TextConverterType.NONE != converterType) {
            return switch (converterType) {
                case XML_FRAGMENT -> AceEditorMode.STROOM_FRAGMENT_PARSER;
                case DATA_SPLITTER -> AceEditorMode.STROOM_DATA_SPLITTER;
                default -> AceEditorMode.STROOM_COMBINED_PARSER;
            };
        } else if (!NullSafe.isBlankString(code)) {
            if (code.contains("dataSplitter")) {
                return AceEditorMode.STROOM_DATA_SPLITTER;
            } else if (code.contains("!ENTITY")) {
                return AceEditorMode.STROOM_FRAGMENT_PARSER;
            }
        }
        // Fall back when the type isn't known
        return AceEditorMode.STROOM_COMBINED_PARSER;
    }
}
