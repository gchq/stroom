/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.client.presenter;

import stroom.planb.client.view.ArchivalSettingsView;
import stroom.planb.client.view.SharedFileStoreView;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.SharedFileStoreSettings;

/**
 * Static helpers shared by every sharding-capable settings presenter.
 *
 * <p>Centralises the read/write logic for {@link SharedFileStoreSettings}
 * (which now includes nested {@link ArchivalSettings}) so it is not duplicated
 * across {@code TraceSettingsPresenter} and any future presenters that expose
 * sharding or archival controls.
 *
 * <p>Usage (read side):
 * <pre>
 *     SharedFileStorePresenterUtil.readSharedFileStore(settings, getView(), getView());
 * </pre>
 *
 * <p>Usage (write side):
 * <pre>
 *     SharedFileStoreSettings sfs = SharedFileStorePresenterUtil.writeSharedFileStore(getView(), getView());
 * </pre>
 */
public final class SharedFileStorePresenterUtil {

    private SharedFileStorePresenterUtil() {
        // utility class
    }

    // -----------------------------------------------------------------------
    // Read helpers
    // -----------------------------------------------------------------------

    /**
     * Populates the Enable Shared File Store checkbox, Path, Shard Count, and
     * archival fields from the {@link HasSharedFileStore} settings object.
     */
    public static void readSharedFileStore(final Object settings,
                                           final SharedFileStoreView sharedFileStoreView,
                                           final ArchivalSettingsView archivalView) {
        if (settings instanceof final HasSharedFileStore capable) {
            final SharedFileStoreSettings sfs = capable.getSharedFileStore();
            if (sfs != null) {
                final boolean hasPath = sfs.getSharedPath() != null
                        && !sfs.getSharedPath().isBlank();
                sharedFileStoreView.setEnableSharedFileStore(hasPath);
                sharedFileStoreView.setSharedPath(sfs.getSharedPath());
                sharedFileStoreView.setShardCount(sfs.getShardCount());
                archivalView.setArchival(sfs.getArchival());
            } else {
                sharedFileStoreView.setEnableSharedFileStore(false);
                sharedFileStoreView.setSharedPath(null);
                sharedFileStoreView.setShardCount(0);
                archivalView.setArchival(null);
            }
        } else {
            sharedFileStoreView.setEnableSharedFileStore(false);
            sharedFileStoreView.setSharedPath(null);
            sharedFileStoreView.setShardCount(0);
            archivalView.setArchival(null);
        }
    }

    // -----------------------------------------------------------------------
    // Write helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a {@link SharedFileStoreSettings} from the current view state,
     * including the nested archival policy.
     *
     * <p>Returns {@code null} only when the shared file store is disabled and
     * shard count is zero — i.e. no shared-file-store configuration at all.
     * When the shared file store is disabled the shared path is treated as absent
     * even if the field contains a value.
     */
    public static SharedFileStoreSettings writeSharedFileStore(final SharedFileStoreView sharedFileStoreView,
                                                               final ArchivalSettingsView archivalView) {
        final int shardCount = sharedFileStoreView.getShardCount();
        final String sharedPath = sharedFileStoreView.isEnableSharedFileStore()
                ? sharedFileStoreView.getSharedPath()
                : null;
        final boolean hasPath = sharedPath != null && !sharedPath.isBlank();
        if (shardCount > 0 || hasPath) {
            return new SharedFileStoreSettings(shardCount, sharedPath, archivalView.getArchival());
        }
        return null;
    }
}
