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

package stroom.pathways.client.presenter;

/**
 * What a drawing's own buttons can ask for.
 *
 * <p>A drawing puts its controls in its markup and says what a click on one means; the view around it
 * carries the request out. Without this the view had to know the id of every button a particular
 * drawing happens to have, and what each of them does — so adding a drawing meant editing the view.
 */
interface PathwayControls {

    void zoomIn();

    void zoomOut();

    /**
     * Scaled so the whole model fits, and put in the middle of the panel.
     */
    void zoomToExtent();

    void toggleLegend();
}
