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

package stroom.search.elastic;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.NullSafe;

import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Checks the field settings of an [ElasticIndexDoc] before it is saved.
///
/// Checks against the index mapping are skipped when the mapping holds no fields, for example because the cluster
/// could not be reached, so a doc can still be saved. Searches check the same rules again.
public final class ElasticFieldSettingsValidator {

    private ElasticFieldSettingsValidator() {
        // Static utility
    }

    /// @param doc    the doc to check
    /// @param fields the doc's fields, as read from the index mapping
    /// @throws EntityServiceException describing the first problem found
    public static void validate(final ElasticIndexDoc doc, final List<ElasticIndexField> fields) {
        final Map<String, ElasticIndexField> mappedFields = new HashMap<>();
        NullSafe.list(fields).forEach(field -> mappedFields.put(field.getFldName(), field));

        final Set<String> settingsNames = new HashSet<>();
        final Set<String> virtualNames = new HashSet<>();
        for (final ElasticIndexFieldSettings settings : doc.getFieldSettings()) {
            final String fieldName = settings.getFieldName();
            if (NullSafe.isBlankString(fieldName)) {
                throw new EntityServiceException("A field setting has no field name");
            }
            if (!settingsNames.add(fieldName)) {
                throw new EntityServiceException("Field '" + fieldName + "' has more than one set of settings");
            }
            if (settings.isVirtual()) {
                virtualNames.add(fieldName);
            }
        }

        for (final ElasticIndexFieldSettings settings : doc.getFieldSettings()) {
            if (settings.isVirtual()) {
                validateVirtualField(settings, mappedFields, virtualNames);
            }
        }
    }

    private static void validateVirtualField(final ElasticIndexFieldSettings settings,
                                             final Map<String, ElasticIndexField> mappedFields,
                                             final Set<String> virtualNames) {
        final String fieldName = settings.getFieldName();
        if (mappedFields.containsKey(fieldName)) {
            throw new EntityServiceException("Virtual field '" + fieldName + "' has the same name as a field of " +
                                             "the index");
        }
        if (settings.getVirtualGroups().isEmpty()) {
            throw new EntityServiceException("Virtual field '" + fieldName + "' has no groups");
        }
        final Set<String> groupNames = new HashSet<>();
        for (final ElasticVirtualFieldGroup group : settings.getVirtualGroups()) {
            final String groupName = group.getName();
            if (NullSafe.isBlankString(groupName)) {
                throw new EntityServiceException("A group of virtual field '" + fieldName + "' has no name");
            }
            if (!groupNames.add(groupName)) {
                throw new EntityServiceException("Virtual field '" + fieldName + "' has more than one group named '" +
                                                 groupName + "'");
            }
            final ExpressionOperator expression = group.getExpression();
            if (expression == null || !hasEnabledTerm(expression)) {
                throw new EntityServiceException("Group '" + groupName + "' of virtual field '" + fieldName +
                                                 "' has no conditions");
            }
            validateTermFields(fieldName, groupName, expression, mappedFields, virtualNames);
        }
    }

    private static boolean hasEnabledTerm(final ExpressionItem item) {
        if (!item.enabled()) {
            return false;
        }
        if (item instanceof final ExpressionOperator operator) {
            return NullSafe.list(operator.getChildren())
                    .stream()
                    .anyMatch(ElasticFieldSettingsValidator::hasEnabledTerm);
        }
        return item instanceof ExpressionTerm;
    }

    private static void validateTermFields(final String fieldName,
                                           final String groupName,
                                           final ExpressionItem item,
                                           final Map<String, ElasticIndexField> mappedFields,
                                           final Set<String> virtualNames) {
        if (item instanceof final ExpressionOperator operator) {
            NullSafe.list(operator.getChildren()).forEach(child ->
                    validateTermFields(fieldName, groupName, child, mappedFields, virtualNames));
        } else if (item instanceof final ExpressionTerm term && term.enabled()) {
            final String termField = NullSafe.get(term.getField(), String::trim);
            if (virtualNames.contains(termField)) {
                throw new EntityServiceException("Group '" + groupName + "' of virtual field '" + fieldName +
                                                 "' uses virtual field '" + termField + "'. Groups may only use " +
                                                 "keyword fields of the index.");
            }
            if (!mappedFields.isEmpty()) {
                final ElasticIndexField termIndexField = mappedFields.get(termField);
                if (termIndexField == null
                    || !Kind.Keyword.jsonValue().equals(termIndexField.getNativeType())
                    || termIndexField.hasTypeConflict()) {
                    throw new EntityServiceException("Group '" + groupName + "' of virtual field '" + fieldName +
                                                     "' uses field '" + termField + "', which is not a keyword " +
                                                     "field of the index");
                }
            }
        }
    }
}
