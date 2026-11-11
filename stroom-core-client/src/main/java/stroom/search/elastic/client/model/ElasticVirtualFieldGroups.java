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

package stroom.search.elastic.client.model;

import stroom.query.api.ExpressionOperator;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;

/// Edits of the group list of a virtual field. Each method returns a new list and leaves the one passed in
/// unchanged.
public final class ElasticVirtualFieldGroups {

    static final String ERROR_EMPTY_NAME = "Enter a name for the group.";
    static final String ERROR_NAME_IN_USE = "Another group already has this name.";

    private ElasticVirtualFieldGroups() {
        // Static utility
    }

    /// @param groups     the existing groups
    /// @param name       the new group's name, which is trimmed
    /// @param expression the new group's expression
    /// @return the groups with the new group added at the end, or the groups unchanged if the name is not valid
    public static List<ElasticVirtualFieldGroup> add(final List<ElasticVirtualFieldGroup> groups,
                                                     final String name,
                                                     final ExpressionOperator expression) {
        final List<ElasticVirtualFieldGroup> result = new ArrayList<>(NullSafe.list(groups));
        if (validateName(groups, -1, name) == null) {
            result.add(new ElasticVirtualFieldGroup(NullSafe.trim(name), expression));
        }
        return result;
    }

    /// @param groups the existing groups
    /// @param index  the position of the group to remove
    /// @return the groups without the one at `index`, or the groups unchanged if `index` is out of range
    public static List<ElasticVirtualFieldGroup> remove(final List<ElasticVirtualFieldGroup> groups,
                                                        final int index) {
        final List<ElasticVirtualFieldGroup> result = new ArrayList<>(NullSafe.list(groups));
        if (index >= 0 && index < result.size()) {
            result.remove(index);
        }
        return result;
    }

    /// Checks a new name for a group.
    ///
    /// @param groups  the existing groups
    /// @param index   the position of the group being renamed, or -1 for a new group
    /// @param newName the proposed name
    /// @return a message describing why the name can't be used, or `null` if it can
    public static String validateName(final List<ElasticVirtualFieldGroup> groups,
                                      final int index,
                                      final String newName) {
        final String trimmed = NullSafe.trim(newName);
        if (trimmed.isEmpty()) {
            return ERROR_EMPTY_NAME;
        }
        final int existingIndex = indexOf(groups, trimmed);
        if (existingIndex >= 0 && existingIndex != index) {
            return ERROR_NAME_IN_USE;
        }
        return null;
    }

    /// @param groups     the existing groups
    /// @param index      the position of the group to replace
    /// @param name       the group's new name, which is trimmed
    /// @param expression the group's new expression
    /// @return the groups with the one at `index` given the new name and expression, or the groups unchanged if the
    /// name is not valid or `index` is out of range
    public static List<ElasticVirtualFieldGroup> replace(final List<ElasticVirtualFieldGroup> groups,
                                                         final int index,
                                                         final String name,
                                                         final ExpressionOperator expression) {
        final List<ElasticVirtualFieldGroup> result = new ArrayList<>(NullSafe.list(groups));
        if (index >= 0 && index < result.size() && validateName(groups, index, name) == null) {
            result.set(index, new ElasticVirtualFieldGroup(NullSafe.trim(name), expression));
        }
        return result;
    }

    private static int indexOf(final List<ElasticVirtualFieldGroup> groups, final String name) {
        final List<ElasticVirtualFieldGroup> list = NullSafe.list(groups);
        for (int i = 0; i < list.size(); i++) {
            if (name.equals(list.get(i).getName())) {
                return i;
            }
        }
        return -1;
    }
}
