/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.meta.shared;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class MetaExpressionUtil {

    private MetaExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createStatusExpression(final Status status) {
        return ExpressionUtil.equalsText(MetaFields.STATUS, status.getDisplayValue());
    }

    public static ExpressionOperator createDataIdSetExpression(final Set<Long> idSet) {
        Objects.requireNonNull(idSet);
        if (idSet.size() == 1) {
            // No point using an IN list for one ID
            return createDataIdExpression(idSet.iterator().next());
        } else {
            final String delimitedList = idSet.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            return ExpressionOperator.builder().op(Op.AND)
                    .addTerm(MetaFields.ID.getFldName(), Condition.IN, delimitedList)
                    .build();
        }
    }

    public static ExpressionOperator createDataIdExpression(final long id) {
        return ExpressionUtil.equalsId(MetaFields.ID, id);
    }

    public static ExpressionOperator createDataIdExpression(final long id, final Status status) {
        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, id)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createParentIdExpression(final long parentId, final Status status) {
        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, parentId)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createTypeExpression(final String typeName, final Status status) {
        return ExpressionOperator.builder()
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, typeName)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return ExpressionOperator.builder()
                .addOperator(
                        ExpressionOperator
                                .builder()
                                .op(Op.OR)
                                .addDocRefTerm(MetaFields.FEED, Condition.IN_FOLDER, folder)
                                .addDocRefTerm(MetaFields.PIPELINE, Condition.IN_FOLDER, folder)
                                .build()
                )
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedExpression(final DocRef feedRef) {
        return ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feedRef)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feedName)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedsExpression(final String... feedNames) {
        return ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.IN, String.join(",", feedNames))
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static boolean hasAdvancedCriteria(final ExpressionOperator expression) {
        final Status status = getSingleStatus(expression);

        if (!Status.UNLOCKED.equals(status)) {
            return true;
        }

        final Set<String> statusPeriod = getTerms(expression, MetaFields.STATUS_TIME);
        return !statusPeriod.isEmpty();
    }

    public static Status getSingleStatus(final ExpressionOperator expression) {
        final Set<Status> streamStatuses = getStatusSet(expression);
        if (streamStatuses.size() == 1) {
            return streamStatuses.iterator().next();
        }
        return null;
    }

    public static Status getSingleStatus(final FindMetaCriteria criteria) {
        if (criteria == null) {
            return null;
        }
        return getSingleStatus(criteria.getExpression());
    }

    public static Set<Status> getStatusSet(final ExpressionOperator expression) {
        final Set<String> terms = getTerms(expression, MetaFields.STATUS);
        final Set<Status> streamStatuses = new HashSet<>();
        for (final String term : terms) {
            for (final Status streamStatus : Status.values()) {
                if (streamStatus.getDisplayValue().equals(term)) {
                    streamStatuses.add(streamStatus);
                }
            }
        }

        return streamStatuses;
    }

    private static Set<String> getTerms(final ExpressionOperator expression, final QueryField field) {
        final Set<String> terms = new HashSet<>();
        if (expression != null) {
            getTerms(expression, field, terms);
        }
        return terms;
    }

    private static void getTerms(final ExpressionOperator expressionOperator,
                          final QueryField field,
                          final Set<String> terms) {
        if (expressionOperator.enabled() && expressionOperator.getChildren() != null) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        if (field.getFldName().equals(((ExpressionTerm) item).getField())) {
                            terms.add(((ExpressionTerm) item).getValue());
                        }
                    } else if (item instanceof ExpressionOperator) {
                        getTerms((ExpressionOperator) item, field, terms);
                    }
                }
            }
        }
    }
}
