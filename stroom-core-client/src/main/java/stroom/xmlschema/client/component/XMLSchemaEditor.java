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

package stroom.xmlschema.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.component.CodeTabController;
import stroom.entity.client.component.DocumentEditor;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.security.client.component.DocumentUserPermissionsTabController;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.xsdbrowser.client.component.XSDBrowserController;
import stroom.widget.xsdbrowser.client.view.XSDModel;
import stroom.xmlschema.shared.XmlSchemaDoc;
import stroom.xmlschema.shared.XmlSchemaResource;
import stroom.xmlschema.shared.XmlSchemaValidationResponse;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.function.Consumer;

/// An XML schema's editor: its Graphical (the schema browsed), Text, Settings, Documentation and
/// Permissions tabs (see [DocumentEditor]). Beside Save and Save As, an indicator shows whether
/// the schema is valid, checked as it is read and as it is typed; saving an invalid schema asks
/// the user to confirm.
public class XMLSchemaEditor extends DocumentEditor<XmlSchemaDoc> {

    private static final XmlSchemaResource XML_SCHEMA_RESOURCE = GWT.create(XmlSchemaResource.class);
    private static final String VALID = "Schema is valid";
    private static final String VALID_TITLE = "Show Validation Result (schema is valid)";
    private static final String INVALID_TITLE = "Show Validation Result (schema is invalid)";
    private static final int VALIDATION_DEBOUNCE_MS = 300;

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData GRAPHICAL = new TabDataImpl("Graphical");
    private static final TabData TEXT = new TabDataImpl("Text");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final RestFactory restFactory;
    // The schema the Graphical tab browses
    private final XSDModel data = new XSDModel();
    private final ToolbarButton validationIndicator;
    private final Timer validationDebounceTimer;
    private CodeTabController text;
    // Whether the text has changed since the Graphical tab last showed it
    private boolean updateDiagram;
    private XmlSchemaValidationResponse validationResponse = new XmlSchemaValidationResponse(true, null);

    /// Makes the editor, with its Graphical, Text, Settings, Documentation and Permissions tabs.
    @Inject
    public XMLSchemaEditor(final EventBus eventBus,
                           final LinkTabPanelView view,
                           final Provider<XMLSchemaSettingsController> settingsProvider,
                           final Provider<XSDBrowserController> xsdBrowserProvider,
                           final Provider<CodeTabController> codeTabProvider,
                           final Provider<MarkdownEditController> markdownEditProvider,
                           final Provider<DocumentUserPermissionsTabController> permissionsProvider,
                           final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
        validationIndicator = getToolbar().button(SvgImage.OK, VALID_TITLE, this::showValidationResult);
        validationDebounceTimer = new Timer() {
            @Override
            public void run() {
                validateSchema(result -> {
                    validationResponse = result;
                    updateValidationIndicator();
                });
            }
        };
        onDispose(validationDebounceTimer::cancel);

        addTab(GRAPHICAL,
                () -> {
                    final XSDBrowserController xsdBrowser = xsdBrowserProvider.get();
                    xsdBrowser.setModel(data);
                    return xsdBrowser;
                },
                // It shows the schema's model, which is read from the document's text when shown
                (xsdBrowser, docRef, doc) -> {
                });
        addTab(TEXT,
                () -> {
                    text = codeTabProvider.get();
                    text.setMode(AceEditorMode.XML);
                    text.getEditor().getOptions().getIndicatorsOption().setAvailable(false);
                    text.getEditor().getOptions().getIndicatorsOption().setOn(false);
                    text.getEditor().getOptions().getLineNumbersOption().setAvailable(true);
                    text.getEditor().getOptions().getLineNumbersOption().setOn(true);
                    text.setOnChange(this::onTextChange);
                    return text;
                },
                (textTab, docRef, doc) -> textTab.setText(doc.getData(), true),
                (textTab, doc) -> doc.copy().data(textTab.getText().trim()).build());
        addTab(SETTINGS,
                settingsProvider::get,
                XMLSchemaSettingsController::read,
                XMLSchemaSettingsController::write);
        addDocumentationTab(DOCUMENTATION,
                markdownEditProvider,
                XmlSchemaDoc::getDescription,
                (doc, description) -> doc.copy().description(description).build());
        addPermissionsTab(PERMISSIONS, permissionsProvider);
        selectTab(GRAPHICAL);
    }

    @Override
    protected void onRead(final DocRef docRef, final XmlSchemaDoc doc) {
        data.setContents(doc.getData());
        // Even for read-only, validate once so the indicator is correct
        validationDebounceTimer.cancel();
        validationDebounceTimer.schedule(0);
    }

    @Override
    protected void afterSelectTab(final TabData tab) {
        if (GRAPHICAL.equals(tab) && updateDiagram) {
            updateDiagram = false;
            data.setContents(text != null
                    ? text.getText()
                    : getDocument().getData());
        }
    }

    @Override
    protected void beforeSave(final Runnable save) {
        validateSchema(result -> {
            if (!result.isOk()) {
                ConfirmEvent.fire(this,
                        "The XML schema appears to be invalid. Are you sure you want to save?",
                        confirmed -> {
                            if (confirmed) {
                                save.run();
                            }
                        });
            } else {
                save.run();
            }
        });
    }

    private void onTextChange() {
        onChange();
        updateDiagram = true;
        validationDebounceTimer.cancel();
        validationDebounceTimer.schedule(VALIDATION_DEBOUNCE_MS);
    }

    private void showValidationResult() {
        if (validationResponse.isOk()) {
            AlertEvent.fireInfo(this, VALID, null);
        } else {
            AlertEvent.fireWarn(this, validationResponse.getError(), null);
        }
    }

    private void validateSchema(final Consumer<XmlSchemaValidationResponse> consumer) {
        final String schemaText;
        if (text != null) {
            schemaText = text.getText();
        } else if (getDocument() != null) {
            schemaText = getDocument().getData();
        } else {
            schemaText = "";
        }
        final String payload = schemaText == null
                ? ""
                : schemaText.trim();
        restFactory
                .create(XML_SCHEMA_RESOURCE)
                .method(resource -> resource.validate(payload))
                .onSuccess(consumer)
                .owner(this)
                .exec();
    }

    private void updateValidationIndicator() {
        if (validationResponse.isOk()) {
            validationIndicator.setTitle(VALID_TITLE);
            validationIndicator.setSvg(SvgImage.OK);
        } else {
            validationIndicator.setTitle(INVALID_TITLE);
            validationIndicator.setSvg(SvgImage.ALERT);
        }
    }
}
