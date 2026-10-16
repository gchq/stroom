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
 * The part of a drawing the model is actually in, measured from the drawing's own corner and at the
 * size it was laid out at rather than the size it is shown at.
 *
 * <p>Not always the whole drawing. A step taken in place keeps the box the drawing already had, so
 * that the corner every node is measured from cannot shift and carry the whole picture with it — and
 * a model that has shrunk since then leaves room in the box that nothing is drawn in. Fitting the
 * drawing to the panel has to frame the model rather than that room, or a model that shrank is shown
 * too small and off to one side.
 */
record DrawnArea(int left, int top, int width, int height) {

}
