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

package stroom.xmlschema.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.AbstractTabProvider;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.xsdbrowser.client.presenter.XSDBrowserPresenter;
import stroom.widget.xsdbrowser.client.view.XSDModel;
import stroom.xmlschema.shared.XmlSchemaDoc;
import stroom.xmlschema.shared.XmlSchemaResource;
import stroom.xmlschema.shared.XmlSchemaValidationResponse;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.PresenterWidget;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.function.Consumer;
import javax.inject.Provider;

public class XMLSchemaPresenter extends DocumentEditTabPresenter<LinkTabPanelView, XmlSchemaDoc> {

    private static final XmlSchemaResource XML_SCHEMA_RESOURCE = GWT.create(XmlSchemaResource.class);

    private static final String VALID = "Schema is valid";
    private static final String INVALID = "Schema is invalid";

    private static final int VALIDATION_DEBOUNCE_MS = 300;
    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData GRAPHICAL = new TabDataImpl("Graphical");
    private static final TabData TEXT = new TabDataImpl("Text");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final RestFactory restFactory;
    private XSDBrowserPresenter xsdBrowserPresenter;
    private EditorPresenter codePresenter;
    private final XSDModel data = new XSDModel();
    private boolean updateDiagram;
    private XmlSchemaValidationResponse validationResponse =
            new XmlSchemaValidationResponse(true, null);
    private final SvgButton validationIndicator;
    private final Timer validationDebounceTimer;

    @Inject
    public XMLSchemaPresenter(final EventBus eventBus,
                              final LinkTabPanelView view,
                              final Provider<XMLSchemaSettingsPresenter> settingsPresenterProvider,
                              final Provider<XSDBrowserPresenter> xsdBrowserPresenterProvider,
                              final Provider<EditorPresenter> codePresenterProvider,
                              final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                              final DocumentUserPermissionsTabProvider<XmlSchemaDoc> documentUserPermissionsTabProvider,
                              final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;

        validationIndicator = SvgButton.create(SvgPresets.ALERT);
        validationIndicator.setSvg(SvgImage.OK);
        validationIndicator.setTitle(VALID);
        toolbar.addButton(this.validationIndicator);

        this.validationDebounceTimer = new Timer() {
            @Override
            public void run() {
                // Call the server validation endpoint. The result consumer runs on success.
                validateSchema(result -> {
                    validationResponse = result;
                    // Update state and UI on the UI thread.
                    updateValidationIndicator();
                });
            }
        };

        addTab(GRAPHICAL, new AbstractTabProvider<XmlSchemaDoc, XSDBrowserPresenter>(eventBus) {
            @Override
            protected XSDBrowserPresenter createPresenter() {
                xsdBrowserPresenter = xsdBrowserPresenterProvider.get();
                xsdBrowserPresenter.setModel(data);
                return xsdBrowserPresenter;
            }
        });
        addTab(TEXT, new AbstractTabProvider<XmlSchemaDoc, EditorPresenter>(eventBus) {
            @Override
            protected EditorPresenter createPresenter() {
                codePresenter = codePresenterProvider.get();
                codePresenter.setMode(AceEditorMode.XML);
                codePresenter.getIndicatorsOption().setAvailable(false);
                codePresenter.getIndicatorsOption().setOn(false);
                codePresenter.getLineNumbersOption().setAvailable(true);
                codePresenter.getLineNumbersOption().setOn(true);
                codePresenter.setReadOnly(isReadOnly());
                codePresenter.getFormatAction().setAvailable(!isReadOnly());
                return codePresenter;
            }

            @Override
            public void onRead(final EditorPresenter presenter,
                               final DocRef docRef,
                               final XmlSchemaDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getData(), true);
                if (!readOnly) {
                    // Enable controls based on user permission
                    registerHandler(presenter.addValueChangeHandler(event -> {
                        setDirty(true);
                        updateDiagram = true;

                        // Kick off debounce validation
                        validationDebounceTimer.cancel();
                        validationDebounceTimer.schedule(VALIDATION_DEBOUNCE_MS);
                    }));
                }
            }

            @Override
            public XmlSchemaDoc onWrite(final EditorPresenter presenter, final XmlSchemaDoc document) {
                document.setData(presenter.getText().trim());
                return document;
            }
        });
        addTab(SETTINGS, new DocumentEditTabProvider<>(settingsPresenterProvider::get));
        addTab(DOCUMENTATION, new MarkdownTabProvider<XmlSchemaDoc>(eventBus, markdownEditPresenterProvider) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final XmlSchemaDoc document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public XmlSchemaDoc onWrite(final MarkdownEditPresenter presenter,
                                        final XmlSchemaDoc document) {
                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(GRAPHICAL);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(validationIndicator.addClickHandler(e -> {
            if (validationResponse.isOk()) {
                AlertEvent.fireInfo(this, VALID, null);
            } else {
                AlertEvent.fireWarn(this, validationResponse.getError(), null);
            }
        }));
    }

    private void validateSchema(final Consumer<XmlSchemaValidationResponse> consumer) {
        final String schemaText;
        if (codePresenter != null) {
            schemaText = codePresenter.getText();
        } else if (getEntity() != null) {
            schemaText = getEntity().getData();
        } else {
            schemaText = "";
        }

        final String payload = schemaText == null
                ? ""
                : schemaText.trim();

        restFactory
                .create(XML_SCHEMA_RESOURCE)
                .method(res -> res.validate(payload))
                .onSuccess(consumer)
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    protected void afterSelectTab(final PresenterWidget<?> content) {
        if (content != null) {
            if (content.equals(xsdBrowserPresenter)) {
                if (updateDiagram) {
                    updateDiagram = false;
                    if (codePresenter != null) {
                        data.setContents(codePresenter.getText());
                    } else {
                        data.setContents(getEntity().getData());
                    }
                }
            }
        }
    }

    private void updateValidationIndicator() {
        if (validationResponse.isOk()) {
            validationIndicator.setTitle(VALID);
            validationIndicator.setSvg(SvgImage.OK);
        } else {
            validationIndicator.setTitle(INVALID);
            validationIndicator.setSvg(SvgImage.ALERT);
        }
    }

    @Override
    protected void onRead(final DocRef docRef, final XmlSchemaDoc doc, final boolean readOnly) {
        super.onRead(docRef, doc, readOnly);
        data.setContents(doc.getData());

        // Even for read-only, validate once so the indicator is correct.
        validationDebounceTimer.cancel();
        validationDebounceTimer.schedule(0);
    }

    @Override
    public String getType() {
        return XmlSchemaDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    @Override
    public void save() {
        validateSchema(result -> {
            if (!result.isOk()) {
                ConfirmEvent.fire(this,
                        "The XML schema appears to be invalid. Are you sure you want to save?",
                        confirmed -> {
                            if (confirmed) {
                                super.save();
                            }
                        }
                );
            } else {
                super.save();
            }
        });
    }
}
