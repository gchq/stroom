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

package stroom.quickfilter.shared;

import stroom.util.shared.UserRef;

import java.util.List;

/**
 * Server-side service behind {@link QuickFilterHistoryResource}. Declared here, beside the
 * resource, for the same reason {@code UserPreferencesService} is: the security module has to
 * be able to delete a user's history when it deletes the user without depending on the module
 * that stores it.
 */
public interface QuickFilterHistoryService {

    /**
     * The calling user's recent filters for {@code key}, most recent first, at most the
     * configured history size.
     */
    List<String> fetch(QuickFilterHistoryKey key);

    /**
     * Record that the calling user used {@code filterText} in the quick filter identified by
     * {@code key}. Idempotent in effect: a filter already present moves to the top rather than
     * appearing twice. Blank text, and text longer than
     * {@link QuickFilterHistoryResource#MAX_FILTER_TEXT_LENGTH}, is ignored.
     */
    void recordUse(QuickFilterHistoryKey key, String filterText);

    /**
     * Forget the calling user's recent filters for {@code key}.
     */
    void clear(QuickFilterHistoryKey key);

    /**
     * Forget every recent filter of {@code userRef}, in every context. For user deletion; the
     * caller must hold the permission to delete the user.
     *
     * @return the number of filters forgotten
     */
    int delete(UserRef userRef);
}
