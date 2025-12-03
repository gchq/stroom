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

package stroom.event.logging.api;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.QueryKey;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageResponse;
import stroom.util.shared.Selection;
import stroom.util.shared.UserDesc;
import stroom.util.shared.UserRef;

import event.logging.AdvancedQuery;
import event.logging.AdvancedQueryItem;
import event.logging.And;
import event.logging.CopyMoveOutcome;
import event.logging.Data;
import event.logging.Group;
import event.logging.MultiObject;
import event.logging.Not;
import event.logging.Or;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.Query.Builder;
import event.logging.ResultPage;
import event.logging.SimpleQuery;
import event.logging.Term;
import event.logging.TermCondition;
import event.logging.User;
import event.logging.util.EventLoggingUtil;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StroomEventLoggingUtil {

    private StroomEventLoggingUtil() {
    }

    public static <T> String buildTypeId(final T restResource, final String method) {
        return String.join(".",
                Objects.requireNonNull(restResource.getClass().getSimpleName()),
                Objects.requireNonNull(method));
    }

    public static OtherObject createOtherObject(final DocRef docRef) {
        return createOtherObject(docRef.getType(), docRef.getUuid(), docRef.getName());
    }

    public static MultiObject createOtherObjectMulti(final DocRef docRef) {
        if (docRef == null) {
            return null;
        } else {
            return MultiObject.builder()
                    .addObject(createOtherObject(docRef))
                    .build();
        }
    }

    public static OtherObject createOtherObject(final String type,
                                                final String uuid,
                                                final String name) {
        return OtherObject.builder()
                .withType(type)
                .withId(uuid)
                .withName(name)
                .build();
    }

    public static User createUser(final stroom.security.shared.User user) {
        Objects.requireNonNull(user);
        return createUser(user.asRef());
    }

    public static User createUser(final UserRef userRef) {
        Objects.requireNonNull(userRef);
        final User.Builder<Void> builder = User.builder()
                .withId(userRef.getSubjectId())
                .withName(userRef.getDisplayName());
        if (userRef.getUuid() != null) {
            builder.addData(Data.builder()
                    .withName("uuid")
                    .withValue(userRef.getUuid())
                    .build());
        }
        if (userRef.isGroup()) {
            builder.withType("user group");
        }
        return builder.build();
    }

    public static User createUser(final UserDesc userDesc) {
        Objects.requireNonNull(userDesc);
        final User.Builder<Void> builder = User.builder()
                .withId(userDesc.getSubjectId())
                .withName(userDesc.getDisplayName());
        return builder.build();
    }

    public static Group createGroup(final UserRef group) {
        Objects.requireNonNull(group);
        if (!group.isGroup()) {
            throw new RuntimeException(("Group '" + group.getDisplayName() + "' is a user not a group"));
        }
        return Group.builder()
                .withId(group.getSubjectId())
                .withName(group.getDisplayName())
                .build();
    }

    public static Group createGroup(final stroom.security.shared.User group) {
        Objects.requireNonNull(group);
        if (!group.isGroup()) {
            throw new RuntimeException(("Group '" + group.getDisplayName() + "' is a user not a group"));
        }
        return Group.builder()
                .withId(group.getSubjectId())
                .withName(group.getDisplayName())
                .build();
    }

    public static Data createData(final String name, final String value) {
        return Data.builder()
                .withName(name)
                .withValue(value)
                .build();
    }

    public static CopyMoveOutcome createCopyMoveOutcome(final Throwable throwable) {
        final CopyMoveOutcome outcome;
        if (throwable != null) {
            outcome = CopyMoveOutcome.builder()
                    .withSuccess(Boolean.FALSE)
                    .withDescription(throwable.getMessage() != null
                            ? throwable.getMessage()
                            : throwable.getClass().getName())
                    .build();
        } else {
            outcome = null;
        }
        return outcome;
    }

    public static ResultPage createResultPage(final stroom.util.shared.ResultPage<?> resultPage) {
        final ResultPage result;
        if (resultPage == null) {
            result = null;
        } else {
            final PageResponse pageResponse = resultPage.getPageResponse();
            if (resultPage.getPageResponse() != null) {
                result = ResultPage.builder()
                        .withFrom(BigInteger.valueOf(pageResponse.getOffset()))
                        .withPerPage(BigInteger.valueOf(pageResponse.getLength()))
                        .build();
            } else {
                result = null;
            }
        }
        return result;
    }

    public static void appendSelection(final Query.Builder<Void> queryBuilder,
                                       final Selection<?> selection) {
        if (selection != null) {
            if (selection.isMatchAll()) {
                queryBuilder
                        .withAdvanced(AdvancedQuery.builder()
                                .addAnd(And.builder()
                                        .build())
                                .build());
            } else if (selection.isMatchNothing()) {
                queryBuilder
                        .withAdvanced(AdvancedQuery.builder()
                                .addNot(Not.builder()
                                        .build())
                                .build());
            } else {
                queryBuilder.withSimple(SimpleQuery.builder()
                        .withInclude(selection.toString())
                        .build());
            }
        }
    }

    public static Query convertExpression(final ExpressionItem expressionItem) {
        return convertExpression(null, expressionItem);
    }

    public static Query convertExpression(final QueryKey queryKey,
                                          final ExpressionItem expressionItem) {
        final Builder<Void> builder = Query.builder();
        NullSafe.consume(queryKey, key -> builder.withId(key.getUuid()));
        appendExpression(builder, expressionItem);
        return builder.build();
    }

    public static void appendExpression(final Query.Builder<Void> queryBuilder,
                                        final ExpressionItem expressionItem) {
        final AdvancedQueryItem advancedQueryItem = convertItem(expressionItem);

        if (advancedQueryItem != null) {
            queryBuilder.withAdvanced(AdvancedQuery.builder()
                    .withQueryItems(advancedQueryItem)
                    .build());
        }
    }

    private static AdvancedQueryItem convertItem(final ExpressionItem expressionItem) {
        if (expressionItem != null && expressionItem.enabled()) {
            if (expressionItem instanceof final ExpressionTerm expressionTerm) {
                return convertTerm(expressionTerm);

            } else if (expressionItem instanceof final ExpressionOperator expressionOperator) {
                if (expressionOperator.getChildren() != null) {
                    final List<AdvancedQueryItem> children = expressionOperator.getChildren()
                            .stream()
                            .map(StroomEventLoggingUtil::convertItem)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    if (!children.isEmpty()) {
                        if (expressionOperator.op().equals(Op.AND)) {
                            return And.builder().withQueryItems(children).build();
                        } else if (expressionOperator.op().equals(Op.OR)) {
                            return Or.builder().withQueryItems(children).build();
                        } else if (expressionOperator.op().equals(Op.NOT)) {
                            return Not.builder().withQueryItems(children).build();
                        } else {
                            throw new RuntimeException("Unknown op " + expressionOperator.op());
                        }
                    }
                }
            } else {
                throw new RuntimeException("Unknown type " + expressionItem.getClass().getName());
            }
        }
        return null;
    }

    private static AdvancedQueryItem convertTerm(final ExpressionTerm expressionTerm) {
        if (expressionTerm.getField() == null ||
            expressionTerm.getCondition() == null) {
            return null;
        }

        final Condition condition = expressionTerm.getCondition();
        final AdvancedQueryItem result;
        if (condition.equals(Condition.IN)) {
            final String[] parts = expressionTerm.getValue().split(",");
            if (parts.length >= 2) {
                result = Or.builder()
                        .addTerm(Arrays.stream(parts)
                                .map(part -> Term.builder()
                                        .withName(expressionTerm.getField())
                                        .withCondition(TermCondition.EQUALS)
                                        .withValue(part)
                                        .build())
                                .collect(Collectors.toList()))
                        .build();
            } else {
                result = EventLoggingUtil.createTerm(
                        expressionTerm.getField(),
                        TermCondition.EQUALS,
                        expressionTerm.getValue());
            }
        } else if (condition.equals(Condition.BETWEEN)) {
            final String[] parts = expressionTerm.getValue().split(",");
            if (parts.length >= 2) {
                result = And.builder()
                        .addTerm(Term.builder()
                                .withName(expressionTerm.getField())
                                .withCondition(TermCondition.GREATER_THAN_EQUAL_TO)
                                .withValue(parts[0])
                                .build())
                        .addTerm(Term.builder()
                                .withName(expressionTerm.getField())
                                .withCondition(TermCondition.LESS_THAN)
                                .withValue(parts[1])
                                .build())
                        .build();
            } else {
                result = EventLoggingUtil.createTerm(
                        expressionTerm.getField(),
                        TermCondition.EQUALS,
                        expressionTerm.getValue());
            }
        } else {
            final TermCondition termCondition = convertCondition(expressionTerm.getCondition());
            final String value;

            switch (expressionTerm.getCondition()) {
                case IN_DICTIONARY:
                    value = "dictionary: " + expressionTerm.getDocRef();
                    break;
                case IN_FOLDER:
                    value = "folder: " + expressionTerm.getDocRef();
                    break;
                case IS_DOC_REF:
                case OF_DOC_REF:
                    if (expressionTerm.getDocRef() == null) {
                        return null;
                    }
                    value = "docRef: " + expressionTerm.getDocRef();
                    break;
                case IS_USER_REF:
                case USER_HAS_PERM:
                case USER_HAS_OWNER:
                case USER_HAS_DELETE:
                case USER_HAS_EDIT:
                case USER_HAS_VIEW:
                case USER_HAS_USE:
                    if (expressionTerm.getDocRef() == null) {
                        return null;
                    }
                    value = "user " +
                            expressionTerm.getCondition().getDisplayValue() +
                            ": " +
                            expressionTerm.getDocRef();
                    break;
                default:
                    value = expressionTerm.getValue() == null
                            ? ""
                            : expressionTerm.getValue();
            }
            result = Term.builder()
                    .withName(expressionTerm.getField())
                    .withCondition(termCondition)
                    .withValue(value)
                    .build();
        }

        return result;
    }


    private static TermCondition convertCondition(final Condition condition) {
        final TermCondition termCondition;
        switch (condition) {
            case CONTAINS:
                termCondition = TermCondition.CONTAINS;
                break;
            case NOT_EQUALS:
                termCondition = TermCondition.NOT_EQUALS;
                break;
            case GREATER_THAN:
                termCondition = TermCondition.GREATER_THAN;
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                termCondition = TermCondition.GREATER_THAN_EQUAL_TO;
                break;
            case LESS_THAN:
                termCondition = TermCondition.LESS_THAN;
                break;
            case LESS_THAN_OR_EQUAL_TO:
                termCondition = TermCondition.LESS_THAN_EQUAL_TO;
                break;
            case MATCHES_REGEX:
                termCondition = TermCondition.REGEX;
                break;
            default:
                termCondition = TermCondition.EQUALS;
        }
        return termCondition;
    }
}
