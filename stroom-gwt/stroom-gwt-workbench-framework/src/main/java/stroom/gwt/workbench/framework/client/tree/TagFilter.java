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

package stroom.gwt.workbench.framework.client.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/// The sidebar's tag filter, as in React Storybook: the user can include tags, so that only
/// stories with one of them are shown, and exclude tags, so that stories with any of them are
/// hidden.
public class TagFilter {

    /// The tag of stories with a play function.
    public static final String PLAY = "play-fn";
    /// The tag of stories whose source is new in git.
    public static final String NEW = "new";
    /// The tag of stories whose source is modified in git.
    public static final String MODIFIED = "modified";
    /// The tag of stories whose component's source is new or modified in git.
    public static final String RELATED = "related";

    /// Tags that Storybook uses internally, so doesn't offer in the filter.
    private static final Set<String> HIDDEN_TAGS = Set.of("dev", "test", "autodocs", "manifest", PLAY);

    private final Set<String> included = new LinkedHashSet<>();
    private final Set<String> excluded = new LinkedHashSet<>();

    /// @param tag A tag.
    /// @return True if the tag is a change detection status, i.e. new, modified or related.
    public static boolean isStatusTag(final String tag) {
        return NEW.equals(tag) || MODIFIED.equals(tag) || RELATED.equals(tag);
    }

    /// @param tag A tag.
    /// @return True if the tag can be chosen in the filter as a custom tag.
    public static boolean isCustomTag(final String tag) {
        return !HIDDEN_TAGS.contains(tag) && !isStatusTag(tag);
    }

    /// Toggles a tag's checkbox, as in Storybook: a tag that is included or excluded stops being
    /// filtered on, and any other tag is included.
    ///
    /// @param tag The tag.
    public void toggleChecked(final String tag) {
        final boolean wasChecked = included.remove(tag) | excluded.remove(tag);
        if (!wasChecked) {
            included.add(tag);
        }
    }

    /// Swaps a tag between excluded and included, for Storybook's 'Exclude'/'Include' button.
    ///
    /// @param tag The tag.
    public void toggleExcluded(final String tag) {
        if (excluded.remove(tag)) {
            included.add(tag);
        } else {
            included.remove(tag);
            excluded.add(tag);
        }
    }

    /// Includes all the tags, as Storybook's 'Select all' does.
    ///
    /// @param tags The tags.
    public void includeAll(final Collection<String> tags) {
        excluded.clear();
        included.addAll(tags);
    }

    /// Removes all filtering.
    public void clear() {
        included.clear();
        excluded.clear();
    }

    /// @param tag A tag.
    /// @return True if the tag is included.
    public boolean isIncluded(final String tag) {
        return included.contains(tag);
    }

    /// @param tag A tag.
    /// @return True if the tag is excluded.
    public boolean isExcluded(final String tag) {
        return excluded.contains(tag);
    }

    /// @return The number of tags included or excluded.
    public int getActiveCount() {
        return included.size() + excluded.size();
    }

    /// @return The included tags.
    public Set<String> getIncluded() {
        return Collections.unmodifiableSet(included);
    }

    /// @param storyTags A story's tags.
    /// @return True if the story should be shown.
    public boolean matches(final Set<String> storyTags) {
        for (final String tag : excluded) {
            if (storyTags.contains(tag)) {
                return false;
            }
        }
        if (included.isEmpty()) {
            return true;
        }
        for (final String tag : included) {
            if (storyTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}
