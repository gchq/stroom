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

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.config.global.client.ConfigDefaultSetter;
import stroom.config.global.shared.ConfigTarget;
import stroom.explorer.client.component.DocSelectionController;
import stroom.feed.shared.FeedDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// Fills a notification's stream destination ([AnalyticStreamDestination]): the destination feed,
/// chosen with a [DocSelectionController] attached to its picker, and whether a streaming rule
/// writes to the source stream's feed instead.
public class AnalyticStreamDestinationController extends Controller {

    private final AnalyticStreamDestination view;
    private final DocSelectionController feedSelection;
    private final ConfigDefaultSetter configDefaultSetter;
    private ConfigTarget configTarget = ConfigTarget.ANALYTIC_UI_DEFAULT;

    private Runnable onChange = () -> {
    };

    @Inject
    public AnalyticStreamDestinationController(final EventBus eventBus,
                                               final AnalyticStreamDestination view,
                                               final Provider<DocSelectionController> docSelectionProvider,
                                               final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus);
        this.view = view;
        this.configDefaultSetter = configDefaultSetter;
        initWidget(view);

        feedSelection = make(docSelectionProvider);
        feedSelection.attach(view.getDestinationFeedBox());
        feedSelection.setIncludedTypes(FeedDoc.TYPE);
        feedSelection.setRequiredPermissions(DocumentPermission.VIEW);
        own(feedSelection.addSelectionListener(e -> onChange.run()));

        // Only an administrator can change a global property, so don't offer it to anyone else.
        view.setSetDefaultVisible(configDefaultSetter.isAllowed());
        view.setOnSetDefault(this::setDefaultDestinationFeed);
        view.setOnChange(() -> onChange.run());
    }

    /// Reports and analytic rules keep their defaults separately, so the owning document decides
    /// which one this feed would be made the default of.
    ///
    /// @param configTarget The defaults the feed would be made the default of.
    public void setConfigTarget(final ConfigTarget configTarget) {
        this.configTarget = configTarget;
    }

    /// Only a streaming rule has a source feed to fall back to, so the option is disabled for
    /// anything else rather than left looking as though it will do something.
    ///
    /// @param analyticProcessType How the rule is processed.
    public void setAnalyticProcessType(final AnalyticProcessType analyticProcessType) {
        view.setUseSourceFeedIfPossibleEnabled(AnalyticProcessType.STREAMING.equals(analyticProcessType));
    }

    /// @param streamDestination The destination to show, or null to leave it as it is.
    public void read(final NotificationStreamDestination streamDestination) {
        if (streamDestination != null) {
            view.setUseSourceFeedIfPossible(streamDestination.isUseSourceFeedIfPossible());
            feedSelection.setSelectedEntityReference(streamDestination.getDestinationFeed(), true);
        }
    }

    /// @return The destination shown.
    public NotificationStreamDestination write() {
        return new NotificationStreamDestination(
                feedSelection.getSelectedEntityReference(),
                view.isUseSourceFeedIfPossible());
    }

    /// @param onChange What to run when the user changes the destination.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    private void setDefaultDestinationFeed() {
        configDefaultSetter.setDefault(
                this,
                configTarget,
                AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_DESTINATION_FEED,
                feedSelection.getSelectedEntityReference(),
                "destination feed",
                getProvidedTaskMonitor());
    }
}
