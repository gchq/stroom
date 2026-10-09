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

package stroom.visualisation.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.visualisation.client.VisualisationPlugin;
import stroom.visualisation.client.presenter.VisualisationPresenter;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter.VisualisationSettingsView;
import stroom.visualisation.client.view.VisualisationSettingsViewImpl;

// STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master.
// Part of "Make the Visualisation Asset system generic" - upstream's VisualisationAsset*
// classes were generalised into the shared stroom.document.asset subsystem so FloorMap can
// carry assets too. Upstream still has the visualisation-specific version, so a merge will
// try to reinstate it; keep this side and re-point any new upstream code at document.asset.
//
// The rename half of that theme needs watching, because it will NOT present as a conflict.
// Thirteen files moved rather than changed:
//   stroom/visualisation/client/presenter/VisualisationAssets*  ->
//   stroom/document/asset/client/presenter/DocumentAsset*       (plus the matching view/ and
//   .ui.xml files, and assets/VisualisationAsset{TreeItem,sImageResource})
// Upstream still holds the files at the old paths, so a merge from master reinstates them as
// *additions* alongside ours. Git sees no conflict and reports success, leaving two parallel
// asset subsystems compiled in - ours wired up, theirs dormant and drifting. After any merge
// from master, check for a resurrected stroom/visualisation/client/presenter/VisualisationAssets*
// and delete it rather than wiring it back in. This note lives here because a per-file marker
// on a file that never conflicts is a marker nobody reads.
public class VisualisationModule extends PluginModule {
    @Override
    protected void configure() {
        bindPlugin(VisualisationPlugin.class);
        bind(VisualisationPresenter.class);
        bindPresenterWidget(VisualisationSettingsPresenter.class, VisualisationSettingsView.class,
                VisualisationSettingsViewImpl.class);
    }
}
