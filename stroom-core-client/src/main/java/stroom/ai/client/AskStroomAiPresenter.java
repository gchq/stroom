/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiPresenter.AskStroomAiProxy;
import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.AskStroomAiContext;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.data.client.event.ShowAskStroomAiEvent;
import stroom.dispatch.client.RestError;
import stroom.docref.HasDisplayValue;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.main.client.event.DockEvent;
import stroom.main.client.event.DockResizeEvent;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Size;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class AskStroomAiPresenter
        extends MyPresenter<AskStroomAiView, AskStroomAiProxy>
        implements AskStroomAiUiHandlers, ShowAskStroomAiEvent.Handler, AskStroomAiEvent.Handler {

    private static final String MARKDOWN_SECTION_BREAK = "\n\n---\n\n";
    private static final int DEFAULT_DOCK_WIDTH = 350;
    private static final int DEFAULT_DOCK_HEIGHT = 250;

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final MarkdownConverter markdownConverter;
    private final AskStroomAiClient askStroomAiClient;
    private final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider;
    private final UserPreferencesManager userPreferencesManager;
    private AskStroomAiContext data;

    private boolean showing;
    private boolean docked;
    private DockBehaviour currentDockBehaviour;

    @Inject
    public AskStroomAiPresenter(final EventBus eventBus,
                                final AskStroomAiView view,
                                final AskStroomAiProxy askStroomAiProxy,
                                final DocSelectionBoxPresenter docSelectionBoxPresenter,
                                final MarkdownConverter markdownConverter,
                                final AskStroomAiClient askStroomAiClient,
                                final Provider<AskStroomAiConfigPresenter> askStroomAiConfigPresenterProvider,
                                final UserPreferencesManager userPreferencesManager) {
        super(eventBus, view, askStroomAiProxy);
        this.markdownConverter = markdownConverter;
        this.askStroomAiClient = askStroomAiClient;
        this.askStroomAiConfigPresenterProvider = askStroomAiConfigPresenterProvider;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;
        this.userPreferencesManager = userPreferencesManager;

        // Load dock state from user preferences.
        this.currentDockBehaviour = loadDockBehaviourFromPrefs();

        getView().setModelRefSelection(docSelectionBoxPresenter.getView());
        docSelectionBoxPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        docSelectionBoxPresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setUiHandlers(this);

        // Initiate the selection box presenter with the default model if one is set.
        askStroomAiClient.getConfig(config -> {
            if (config.getModelRef() != null) {
                docSelectionBoxPresenter.setSelectedEntityReference(config.getModelRef(), true);
            }
        }, this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(docSelectionBoxPresenter.addDataSelectionHandler(event ->
                askStroomAiClient.getConfig(config -> {
                    final AskStroomAIConfig newConfig = config
                            .copy()
                            .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                            .build();
                    askStroomAiClient.setConfig(newConfig);
                }, this)));

        // Listen for dock splitter resize events to persist the new size.
        addRegisteredHandler(DockResizeEvent.getType(), event -> {
            if (docked) {
                final Size newSize = event.getNewSize();
                final DockLocation loc = currentDockBehaviour.getDockLocation();
                final int dimension;
                if (loc == DockLocation.LEFT || loc == DockLocation.RIGHT) {
                    dimension = (int) newSize.getWidth();
                } else {
                    dimension = (int) newSize.getHeight();
                }
                saveDockSizeToPrefs(dimension);
            }
        });
    }

    @Override
    protected void revealInParent() {

    }

    @ProxyEvent
    @Override
    public void onAsk(final AskStroomAiEvent event) {
        // Show if not already showing.
        ShowAskStroomAiEvent.fire(this, true);

        Scheduler.get().scheduleDeferred(() -> {
            setContext(event.getData());
            getView().focus();
        });
    }

    @ProxyEvent
    @Override
    public void onShow(final ShowAskStroomAiEvent event) {
        if (event.isShow()) {
            if (!showing) {
                showing = true;

                if (currentDockBehaviour.getDockType() == DockType.DOCK) {
                    // Dock mode: fire DockEvent to attach to main layout.
                    final Size size = getDockSize();
                    DockEvent.fire(this, this, currentDockBehaviour, size);
                    docked = true;
                } else {
                    // Dialog mode: show as popup.
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.CLOSE_DIALOG)
                            .popupSize(PopupSize.resizable(700, 500))
                            .caption("Ask Stroom AI")
                            .onHideRequest(e -> {
                                ShowAskStroomAiEvent.fire(this, false);
                            })
                            .onHide(e -> {
                                showing = false;
                            })
                            .fire();
                }
            }
        } else {
            if (showing) {
                showing = false;
                if (currentDockBehaviour.getDockType() == DockType.DOCK) {
                    // Dock mode: fire DockEvent to detach to main layout.
                    docked = false;
                    DockEvent.fireUndock(this, this);
                } else {
                    // Dialog mode: hide popup.
                    HidePopupEvent.builder(this).fire();
                }
            }
        }
    }

    @Override
    public void onChangeConfig() {
        askStroomAiClient.getConfig(config -> {
            final AskStroomAIConfig newConfig = config
                    .copy()
                    .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                    .build();
            askStroomAiConfigPresenterProvider.get().show(
                    newConfig, c -> {
                        askStroomAiClient.setConfig(c);
                    }, currentDockBehaviour, this::onDockBehaviourChange);
        }, this);
    }

    void onDockBehaviourChange(final DockBehaviour dockBehaviour) {
        final DockBehaviour oldBehaviour = this.currentDockBehaviour;
        this.currentDockBehaviour = dockBehaviour;

        // Persist to user preferences.
        saveDockBehaviourToPrefs(dockBehaviour);

        if (showing) {
            final boolean wasDocked = docked;
            final boolean wantsDock = dockBehaviour.getDockType() == DockType.DOCK;

            if (wasDocked && !wantsDock) {
                // Switching from DOCK to DIALOG: undock then show popup.
                DockEvent.fireUndock(this, this);
                docked = false;
                showing = false;
                // Re-trigger show as dialog.
                showAsDialog();
            } else if (!wasDocked && wantsDock) {
                // Switching from DIALOG to DOCK: hide popup then dock.
                HidePopupRequestEvent.builder(this).fire();
                showing = false;
                docked = false;
                // Now dock.
                showing = true;
                docked = true;
                DockEvent.fire(this, this, dockBehaviour, getDockSize());
                getView().focus();
            } else if (wasDocked && wantsDock) {
                // Location change while docked: undock and re-dock.
                DockEvent.fireUndock(this, this);
                DockEvent.fire(this, this, dockBehaviour, getDockSize());
                getView().focus();
            }
            // DIALOG to DIALOG (location change) — nothing to do.
        }
    }

    @ProxyCodeSplit
    public interface AskStroomAiProxy extends Proxy<AskStroomAiPresenter> {

    }

    @Override
    public void setContext(final AskStroomAiContext data) {
        this.data = data;
    }

    @Override
    public void onSendMessage(final String message) {
        getView().setSendButtonLoadingState(true);
        renderMarkdown("> " + message);

        // Scroll markdown container to bottom, so the user's message is displayed
        final Element markdownContainer = getView().getMarkdownContainer();
        markdownContainer.setScrollTop(markdownContainer.getScrollHeight());

        askStroomAiClient.getConfig(config -> {
//            final AskStroomAiRequest request = new AskStroomAiRequest(config, data, message);
//            askStroomAiClient.sendMessage(request,
//                    response -> {
//                        onMessageReceived(response.getMessage());
//                        getView().setSendButtonLoadingState(false);
//                    }, error ->
//                            showError(error, "Stroom AI request failed", () ->
//                                    getView().setSendButtonLoadingState(false)), this);
        }, this);
    }

    private void showError(final RestError error,
                           final String message,
                           final AlertCallback callback) {
        AlertEvent.fireError(
                AskStroomAiPresenter.this,
                message + " - " + error.getMessage(),
                null,
                callback);
    }

    private void onMessageReceived(final String message) {
        final Element markdownContainer = getView().getMarkdownContainer();
        final int oldScrollHeight = markdownContainer.getScrollHeight();

        renderMarkdown(message);

        // Scroll down a little, to display the start of the response message
        markdownContainer.setScrollTop(oldScrollHeight + 50);
    }

    private void renderMarkdown(final String message) {
        final Element markdownContainer = getView().getMarkdownContainer();

        // Emit the rendered markdown as HTML
        final StringBuilder sb = new StringBuilder();
        if (markdownContainer.hasChildNodes()) {
            sb.append(MARKDOWN_SECTION_BREAK);
        }
        sb.append(message);
        final SafeHtml markdownHtml = markdownConverter.convertMarkdownToHtml(sb.toString());
        final DivElement newMarkdownContent = Document.get().createDivElement();
        newMarkdownContent.setInnerSafeHtml(markdownHtml);

        // Append all new markdown nodes to the container
        while (newMarkdownContent.getFirstChild() != null) {
            markdownContainer.appendChild(newMarkdownContent.getFirstChild());
        }
    }

    public interface AskStroomAiView extends View, Focus, HasUiHandlers<AskStroomAiUiHandlers> {

        void setModelRefSelection(View view);

        Element getMarkdownContainer();

        String getMessage();

        void setSendButtonLoadingState(final boolean enabled);
    }

    // ---- Dock preference helpers ----

    private DockBehaviour loadDockBehaviourFromPrefs() {
        final UserPreferences prefs = userPreferencesManager.getCurrentUserPreferences();
        if (prefs != null) {
            final DockType type = parseDockType(prefs.getAiDockType());
            final DockLocation location = parseDockLocation(prefs.getAiDockLocation());
            return new DockBehaviour(type, location);
        }
        return new DockBehaviour(DockType.DIALOG, DockLocation.RIGHT);
    }

    private void saveDockBehaviourToPrefs(final DockBehaviour behaviour) {
        final UserPreferences currentPrefs = userPreferencesManager.getCurrentUserPreferences();
        if (currentPrefs != null) {
            final UserPreferences newPrefs = currentPrefs.copy()
                    .aiDockType(behaviour.getDockType().name())
                    .aiDockLocation(behaviour.getDockLocation().name())
                    .build();
            userPreferencesManager.setCurrentPreferences(newPrefs);
            userPreferencesManager.update(newPrefs, result -> {
            }, this);
        }
    }

    private void saveDockSizeToPrefs(final int size) {
        final UserPreferences currentPrefs = userPreferencesManager.getCurrentUserPreferences();
        if (currentPrefs != null) {
            final UserPreferences newPrefs = currentPrefs.copy()
                    .aiDockSize(size)
                    .build();
            userPreferencesManager.setCurrentPreferences(newPrefs);
            userPreferencesManager.update(newPrefs, result -> {
            }, this);
        }
    }

    private Size getDockSize() {
        final UserPreferences prefs = userPreferencesManager.getCurrentUserPreferences();
        final DockLocation loc = currentDockBehaviour.getDockLocation();
        final int defaultSize;
        if (loc == DockLocation.LEFT || loc == DockLocation.RIGHT) {
            defaultSize = DEFAULT_DOCK_WIDTH;
        } else {
            defaultSize = DEFAULT_DOCK_HEIGHT;
        }
        final int size = (prefs != null && prefs.getAiDockSize() != null)
                ? prefs.getAiDockSize()
                : defaultSize;
        return new Size.Builder()
                .width(size)
                .height(size)
                .build();
    }

    private void showAsDialog() {
        showing = true;
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(700, 500))
                .caption("Ask Stroom AI")
                .onShow(e -> getView().focus())
                .onHide(e -> showing = false)
                .fire();
    }

    private static DockType parseDockType(final String value) {
        if (value != null) {
            try {
                return DockType.valueOf(value);
            } catch (final IllegalArgumentException e) {
                // Ignore invalid values.
            }
        }
        return DockType.DIALOG;
    }

    private static DockLocation parseDockLocation(final String value) {
        if (value != null) {
            try {
                return DockLocation.valueOf(value);
            } catch (final IllegalArgumentException e) {
                // Ignore invalid values.
            }
        }
        return DockLocation.RIGHT;
    }

    public static class DockBehaviour {

        private final DockType dockType;
        private final DockLocation dockLocation;

        public DockBehaviour(final DockType dockType,
                             final DockLocation dockLocation) {
            this.dockType = dockType;
            this.dockLocation = dockLocation;
        }

        public DockType getDockType() {
            return dockType;
        }

        public DockLocation getDockLocation() {
            return dockLocation;
        }
    }

    public enum DockType implements HasDisplayValue {
        DIALOG("Dialog"),
        TAB("Tab"),
        FLOAT("Float"),
        DOCK("Dock");

        private final String displayValue;

        DockType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public enum DockLocation implements HasDisplayValue {
        TOP("Top"),
        LEFT("Left"),
        BOTTOM("Bottom"),
        RIGHT("Right");

        private final String displayValue;

        DockLocation(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
