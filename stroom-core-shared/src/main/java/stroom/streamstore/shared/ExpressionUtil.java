package stroom.streamstore.shared;

import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ExpressionUtil {
    private ExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createSimpleExpression() {
        return createSimpleExpression(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue());
    }

    public static ExpressionOperator createSimpleExpression(final String field, final Condition condition, final String value) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(field, condition, value)
                .build();
    }

    public static ExpressionOperator createStatusExpression(final StreamStatus streamStatus) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, streamStatus.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createStreamExpression(final long streamId) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.STREAM_ID, Condition.EQUALS, String.valueOf(streamId))
                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                .build();
        return expression;
    }

    public static ExpressionOperator createParentStreamExpression(final long parentStreamId) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.PARENT_STREAM_ID, Condition.EQUALS, String.valueOf(parentStreamId))
                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                .build();
        return expression;
    }

    public static ExpressionOperator createStreamTypeExpression(final StreamType streamType) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.STREAM_TYPE, Condition.EQUALS, streamType.getDisplayValue())
                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return createFeedsExpression(feedName);
    }

    public static ExpressionOperator createFeedsExpression(final String... feedNames) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (feedNames != null) {
            if (feedNames.length == 1) {
                builder.addTerm(StreamDataSource.FEED, Condition.EQUALS, feedNames[0]);
            } else {
                final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
                for (final String feedName : feedNames) {
                    or.addTerm(StreamDataSource.FEED, Condition.EQUALS, feedName);
                }
                builder.addOperator(or.build());
            }
        }

        builder.addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue());
        return builder.build();
    }


    public static ExpressionOperator createPipelineExpression(final PipelineDoc pipelineDoc) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(StreamDataSource.PIPELINE, Condition.EQUALS, pipelineDoc.getUuid())
                .addTerm(StreamDataSource.STATUS, Condition.EQUALS, StreamStatus.UNLOCKED.getDisplayValue())
                .build();
    }

    static int termCount(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).size();
    }

    public static List<String> fields(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).stream().map(ExpressionTerm::getField).collect(Collectors.toList());
    }

    public static List<String> fields(final ExpressionOperator expressionOperator, final String field) {
        return terms(expressionOperator, field).stream().map(ExpressionTerm::getField).collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).stream().map(ExpressionTerm::getValue).collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator, final String field) {
        return terms(expressionOperator, field).stream().map(ExpressionTerm::getValue).collect(Collectors.toList());
    }

    public static List<ExpressionTerm> terms(final ExpressionOperator expressionOperator, final String field) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        addTerms(expressionOperator, field, terms);
        return terms;
    }

    private static void addTerms(final ExpressionOperator expressionOperator, final String field, final List<ExpressionTerm> terms) {
        if (expressionOperator != null && expressionOperator.enabled() && !Op.NOT.equals(expressionOperator.getOp())) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        final ExpressionTerm expressionTerm = (ExpressionTerm) item;
                        if ((field == null || field.equals(expressionTerm.getField())) && expressionTerm.getValue() != null && expressionTerm.getValue().length() > 0) {
                            terms.add(expressionTerm);
                        }
                    } else if (item instanceof ExpressionOperator) {
                        addTerms((ExpressionOperator) item, field, terms);
                    }
                }
            }
        }
    }
}
