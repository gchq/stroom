package stroom.task.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorTaskDataSource;
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

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (folders != null) {
            final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
            for (final DocRef folder : folders) {
                or.addTerm(ProcessorTaskDataSource.PIPELINE_UUID, Condition.IN_FOLDER, folder);
//                or.addDocRefTerm(ProcessTaskDataSource.FEED_UUID, Condition.IN_FOLDER, folder);
            }
            builder.addOperator(or.build());
        }

        return builder.build();
    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorTaskDataSource.FEED_NAME, Condition.EQUALS, feedName)
                .build();
    }

    public static ExpressionOperator createPipelineExpression(final PipelineDoc pipelineEntity) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorTaskDataSource.PIPELINE_UUID, Condition.IS_DOC_REF, DocRefUtil.create(pipelineEntity))
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
        if (expressionOperator != null && expressionOperator.isEnabled() && !Op.NOT.equals(expressionOperator.getOp())) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.isEnabled()) {
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
