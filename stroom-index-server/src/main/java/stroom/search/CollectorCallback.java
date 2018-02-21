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

package stroom.search;

public interface CollectorCallback {
    /**
     * Called when the parallel searcher starts reading a new index.
     */
    void onIndexRead();

    /**
     * Called when one of the indexes that is being searched throws an
     * exception.
     *
     * @param e The exception that was thrown by the index.
     */
    void onIndexException(final Exception e);

    /**
     * Called when the parallel searcher finds a new hit.
     */
    void onHit();

    /**
     * Called when the parallel searcher has finished searching all indexes that
     * is has been asked to search.
     */
    void onFinish();
}
