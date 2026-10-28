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

package stroom.feed.client.component;

import stroom.data.client.DataTypeUiManager;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.DataFormatNames;
import stroom.receive.rules.shared.ReceiptCheckMode;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/// A feed's Settings tab ([FeedSettings]): it fetches the encodings, stream types and volume
/// groups to choose from, and allows the feed's status to be chosen only when receipt is checked
/// against it.
public class FeedSettingsController extends Controller {

    @SuppressWarnings("SimplifyStreamApiCallChains") // Cos GWT
    private static final List<String> FORMATS = DataFormatNames.ALL_HARD_CODED_FORMAT_NAMES.stream()
            .sorted()
            .collect(Collectors.toUnmodifiableList());
    private static final FsVolumeGroupResource VOLUME_GROUP_RESOURCE = GWT.create(FsVolumeGroupResource.class);

    private final FeedSettings view;
    private final UiConfigCache uiConfigCache;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The tab.
    /// @param uiConfigCache     Says how receipt is checked.
    /// @param dataTypeUiManager Gives the stream types.
    /// @param restFactory       Fetches the volume groups.
    /// @param feedClient        Fetches the encodings.
    @Inject
    public FeedSettingsController(final EventBus eventBus,
                                  final FeedSettings view,
                                  final UiConfigCache uiConfigCache,
                                  final DataTypeUiManager dataTypeUiManager,
                                  final RestFactory restFactory,
                                  final FeedClient feedClient) {
        super(eventBus);
        this.view = view;
        this.uiConfigCache = uiConfigCache;
        initWidget(view);

        view.setFormats(FORMATS);
        view.setOnChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);

        feedClient.fetchSupportedEncodings(encodings -> {
            if (!isDisposed()) {
                view.setEncodings(encodings);
            }
        }, taskMonitor());
        restFactory
                .create(VOLUME_GROUP_RESOURCE)
                .method(res -> res.find(new ExpressionCriteria()))
                .onSuccess(result -> {
                    final List<String> names = new ArrayList<>();
                    NullSafe.consume(result, ResultPage::getValues, values -> {
                        for (final FsVolumeGroup volumeGroup : values) {
                            names.add(volumeGroup.getName());
                        }
                    });
                    view.setVolumeGroups(names);
                })
                .owner(this)
                .taskMonitorFactory(taskMonitor())
                .exec();
        uiConfigCache.get(extendedUiConfig -> dataTypeUiManager.getTypes(types -> {
            if (!isDisposed()) {
                view.setReceivedTypes(types);
            }
        }, taskMonitor()), taskMonitor());
        updateFeedStatusEnabledState();
    }

    /// Shows a feed's settings.
    ///
    /// @param docRef The feed.
    /// @param feed   Its settings.
    public void read(final DocRef docRef, final FeedDoc feed) {
        view.read(feed);
        updateFeedStatusEnabledState();
    }

    /// Writes the settings shown into a copy of a feed.
    ///
    /// @param feed The feed.
    /// @return A copy of it with the settings shown.
    public FeedDoc write(final FeedDoc feed) {
        return view.write(feed);
    }

    private void updateFeedStatusEnabledState() {
        uiConfigCache.get(extendedUiConfig -> {
            if (!isDisposed()) {
                view.setFeedStatusEnabled(
                        extendedUiConfig.getReceiptCheckMode() == ReceiptCheckMode.FEED_STATUS);
            }
        }, taskMonitor());
    }

}
