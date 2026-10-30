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

package stroom.analytics.client.component;

import stroom.analytics.shared.ReportDoc;
import stroom.config.global.client.ConfigDefaultSetter;
import stroom.config.global.shared.ConfigTarget;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.widget.component.client.Controller;
import stroom.widget.dropdowntree.client.DropDownBox;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Consumer;

/// The error feed of an analytic rule or a report, chosen with a picker ([DropDownBox]) it attaches
/// to: a document with none shows, and is given, the default error feed for its kind, which an
/// administrator can set from it ([#setDefault()]).
///
/// ```java
/// errorFeed = make(errorFeedProvider).attach(view.getErrorFeedBox());
/// view.setSetDefaultVisible(errorFeed.isSetDefaultAllowed());
/// ```
public class ErrorFeedController extends Controller {

    private final Provider<DocSelectionController> docSelectionProvider;
    private final UiConfigCache uiConfigCache;
    private final ConfigDefaultSetter configDefaultSetter;
    private DocSelectionController feed;
    private ConfigTarget configTarget = ConfigTarget.ANALYTIC_UI_DEFAULT;

    /// Made by GIN; [#attach(DropDownBox)] gives it its picker.
    ///
    /// @param eventBus             The application's event bus.
    /// @param docSelectionProvider Makes the picker's controller.
    /// @param uiConfigCache        Gives the default error feeds.
    /// @param configDefaultSetter  Sets the default error feed.
    @Inject
    public ErrorFeedController(final EventBus eventBus,
                               final Provider<DocSelectionController> docSelectionProvider,
                               final UiConfigCache uiConfigCache,
                               final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus);
        this.docSelectionProvider = docSelectionProvider;
        this.uiConfigCache = uiConfigCache;
        this.configDefaultSetter = configDefaultSetter;
    }

    /// Attaches this to the picker it fills.
    ///
    /// @param box The picker, e.g. one a template declares.
    /// @return This, to keep.
    public ErrorFeedController attach(final DropDownBox box) {
        feed = make(docSelectionProvider);
        feed.attach(box);
        feed.setIncludedTypes(FeedDoc.TYPE);
        feed.setRequiredPermissions(DocumentPermission.VIEW);
        return this;
    }

    /// @return Whether the user may set the default error feed: only an administrator can change
    ///         a global property, so it isn't offered to anyone else.
    public boolean isSetDefaultAllowed() {
        return configDefaultSetter.isAllowed();
    }

    /// Shows a document's error feed, or the default for its kind if it has none.
    ///
    /// @param docRef    The document, a rule or a report.
    /// @param errorFeed Its error feed, or null.
    public void read(final DocRef docRef, final DocRef errorFeed) {
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null && !isDisposed()) {
                final boolean isReport = ReportDoc.TYPE.equals(docRef.getType());
                configTarget = isReport
                        ? ConfigTarget.REPORT_UI_DEFAULT
                        : ConfigTarget.ANALYTIC_UI_DEFAULT;
                final DocRef defaultErrorFeed = isReport
                        ? extendedUiConfig.getReportUiDefaultConfig().getDefaultErrorFeed()
                        : extendedUiConfig.getAnalyticUiDefaultConfig().getDefaultErrorFeed();
                final DocRef selectedDocRef = errorFeed != null ? errorFeed : defaultErrorFeed;
                if (selectedDocRef != null) {
                    feed.setSelectedEntityReference(selectedDocRef, true);
                }
            }
        }, taskMonitor());
    }

    /// @return The error feed chosen, or null.
    public DocRef getErrorFeed() {
        return feed.getSelectedEntityReference();
    }

    /// Listens for the user choosing an error feed, until the registration is removed.
    ///
    /// @param listener Told the feed chosen.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<DocRef> listener) {
        return feed.addSelectionListener(listener);
    }

    /// @param readOnly Whether the error feed can't be changed.
    public void setReadOnly(final boolean readOnly) {
        feed.setReadOnly(readOnly);
    }

    /// Makes the error feed chosen the default for documents of this kind, once the user confirms.
    public void setDefault() {
        configDefaultSetter.setDefault(
                this,
                configTarget,
                AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_ERROR_FEED,
                feed.getSelectedEntityReference(),
                "error feed",
                taskMonitor());
    }

}
