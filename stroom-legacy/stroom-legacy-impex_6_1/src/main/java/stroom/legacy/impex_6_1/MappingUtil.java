package stroom.legacy.impex_6_1;

import stroom.index.shared.LuceneIndexField;
import stroom.index.shared.OldIndexFieldType;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.UserTimeZone;
import stroom.query.api.datasource.AnalyzerType;
import stroom.util.shared.time.TimeUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Deprecated
public final class MappingUtil {

    private MappingUtil() {
    }

    public static stroom.docref.DocRef map(final stroom.legacy.model_6_1.DocRef value) {
        if (value == null) {
            return null;
        }
        return new stroom.docref.DocRef(value.getType(), value.getUuid(), value.getName());
    }

    public static List<stroom.docref.DocRef> map(final stroom.legacy.model_6_1.DocRefs value) {
        if (value == null) {
            return null;
        }
        return mapList(value.getDoc(), MappingUtil::map).stream()
                .sorted(stroom.docref.DocRef::compareTo)
                .collect(Collectors.toList());
    }

    public static <T, R> List<R> mapList(final Collection<T> value, final Function<T, R> function) {
        if (value == null) {
            return null;
        }
        return value
                .stream()
                .map(function)
                .collect(Collectors.toList());
    }

    public static stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData map(stroom.legacy.model_6_1.StroomStatsStoreEntityData value) {
        if (value == null) {
            return null;
        }
        List<stroom.statistics.impl.hbase.shared.StatisticField> statisticFields = null;
        if (value.getStatisticFields() != null) {
            statisticFields = value
                    .getStatisticFields()
                    .stream()
                    .map(MappingUtil::map)
                    .collect(Collectors.toList());
        }

        Set<stroom.statistics.impl.hbase.shared.CustomRollUpMask> customRollUpMasks = null;
        if (value.getCustomRollUpMasks() != null) {
            customRollUpMasks = value
                    .getCustomRollUpMasks()
                    .stream()
                    .map(MappingUtil::map)
                    .collect(Collectors.toSet());
        }

        return new stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData(statisticFields, customRollUpMasks);
    }

    public static stroom.statistics.impl.hbase.shared.StatisticField map(stroom.legacy.model_6_1.StatisticField value) {
        if (value == null) {
            return null;
        }
        return new stroom.statistics.impl.hbase.shared.StatisticField(value.getFieldName());
    }

    public static stroom.statistics.impl.hbase.shared.CustomRollUpMask map(stroom.legacy.model_6_1.CustomRollUpMask value) {
        if (value == null) {
            return null;
        }
        return new stroom.statistics.impl.hbase.shared.CustomRollUpMask(value.getRolledUpTagPositions());
    }

    public static stroom.statistics.impl.sql.shared.StatisticsDataSourceData map(stroom.legacy.model_6_1.StatisticsDataSourceData value) {
        if (value == null) {
            return null;
        }
        List<stroom.statistics.impl.sql.shared.StatisticField> statisticFields = null;
        if (value.getStatisticFields() != null) {
            statisticFields = value
                    .getStatisticFields()
                    .stream()
                    .map(MappingUtil::mapStatisticField)
                    .collect(Collectors.toList());
        }

        Set<stroom.statistics.impl.sql.shared.CustomRollUpMask> customRollUpMasks = null;
        if (value.getCustomRollUpMasks() != null) {
            customRollUpMasks = value
                    .getCustomRollUpMasks()
                    .stream()
                    .map(MappingUtil::mapCustomRollupMask)
                    .collect(Collectors.toSet());
        }

        return new stroom.statistics.impl.sql.shared.StatisticsDataSourceData(statisticFields, customRollUpMasks);
    }

    public static stroom.statistics.impl.sql.shared.StatisticField mapStatisticField(stroom.legacy.model_6_1.StatisticField value) {
        if (value == null) {
            return null;
        }
        return new stroom.statistics.impl.sql.shared.StatisticField(value.getFieldName());
    }

    public static stroom.statistics.impl.sql.shared.CustomRollUpMask mapCustomRollupMask(stroom.legacy.model_6_1.CustomRollUpMask value) {
        if (value == null) {
            return null;
        }
        return new stroom.statistics.impl.sql.shared.CustomRollUpMask(value.getRolledUpTagPositions());
    }

    public static stroom.dashboard.shared.DashboardConfig map(stroom.legacy.model_6_1.DashboardConfig value) {
        if (value == null) {
            return null;
        }
        return new stroom.dashboard.shared.DashboardConfig(
                value.getParameters(),
                null,
                mapList(value.getComponents(), MappingUtil::map),
                map(value.getLayout()),
                null,
                null,
                false,
                null);
    }

    public static stroom.dashboard.shared.ComponentConfig map(stroom.legacy.model_6_1.ComponentConfig value) {
        if (value == null) {
            return null;
        }
        return new stroom.dashboard.shared.ComponentConfig(value.getType(),
                value.getId(),
                value.getName(),
                map(value.getSettings()));
    }

    public static stroom.dashboard.shared.ComponentSettings map(stroom.legacy.model_6_1.ComponentSettings value) {
        if (value == null) {
            return null;
        }

        if (value instanceof stroom.legacy.model_6_1.QueryComponentSettings) {
            return map((stroom.legacy.model_6_1.QueryComponentSettings) value);
        } else if (value instanceof stroom.legacy.model_6_1.TableComponentSettings) {
            return map((stroom.legacy.model_6_1.TableComponentSettings) value);
        } else if (value instanceof stroom.legacy.model_6_1.VisComponentSettings) {
            return map((stroom.legacy.model_6_1.VisComponentSettings) value);
        } else if (value instanceof stroom.legacy.model_6_1.TextComponentSettings) {
            return map((stroom.legacy.model_6_1.TextComponentSettings) value);
        }

        return null;
    }

    public static stroom.dashboard.shared.QueryComponentSettings map(stroom.legacy.model_6_1.QueryComponentSettings value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.QueryComponentSettings(map(value.getDataSource()),
                map(value.getExpression()),
                map(value.getAutomate()),
                null,
                null,
                null);
    }

    public static stroom.dashboard.shared.TableComponentSettings map(stroom.legacy.model_6_1.TableComponentSettings value) {
        if (value == null) {
            return null;
        }

        List<Long> maxResults = null;
        if (value.getMaxResults() != null) {
            maxResults = new ArrayList<>();
            for (long results : value.getMaxResults()) {
                maxResults.add(results);
            }
        }

        return new stroom.dashboard.shared.TableComponentSettings(
                value.getQueryId(),
                null,
                mapList(value.getFields(), MappingUtil::map),
                value.getExtractValues(),
                false,
                map(value.getExtractionPipeline()),
                maxResults,
                100,
                value.getShowDetail(),
                mapList(value.getConditionalFormattingRules(), MappingUtil::map),
                value.getModelVersion(),
                false,
                null);
    }

    public static ConditionalFormattingRule map(stroom.legacy.model_6_1.ConditionalFormattingRule value) {
        if (value == null) {
            return null;
        }

        return new ConditionalFormattingRule(
                value.getId(),
                map(value.getExpression()),
                value.isHide(),
                value.getBackgroundColor(),
                value.getTextColor(),
                value.isEnabled(),
                null,
                null,
                null,
                null);
    }

    public static stroom.dashboard.shared.VisComponentSettings map(stroom.legacy.model_6_1.VisComponentSettings value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.VisComponentSettings(
                value.getTableId(),
                map(value.getVisualisation()),
                value.getJSON());
    }

    public static stroom.dashboard.shared.TextComponentSettings map(stroom.legacy.model_6_1.TextComponentSettings value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.TextComponentSettings(
                value.getTableId(),
                mapColumnRef(value.getStreamIdField()),
                mapColumnRef(value.getPartNoField()),
                mapColumnRef(value.getRecordNoField()),
                mapColumnRef(value.getLineFromField()),
                mapColumnRef(value.getColFromField()),
                mapColumnRef(value.getLineToField()),
                mapColumnRef(value.getColToField()),
                map(value.getPipeline()),
                value.isShowAsHtml(),
                value.isShowStepping(),
                value.getModelVersion());
    }

    public static stroom.query.api.ExpressionItem map(stroom.legacy.model_6_1.ExpressionItem value) {
        if (value == null) {
            return null;
        }

        if (value instanceof stroom.legacy.model_6_1.ExpressionOperator) {
            return map((stroom.legacy.model_6_1.ExpressionOperator) value);
        } else if (value instanceof stroom.legacy.model_6_1.ExpressionTerm) {
            return map((stroom.legacy.model_6_1.ExpressionTerm) value);
        }

        return null;
    }

    public static stroom.query.api.ExpressionOperator map(stroom.legacy.model_6_1.ExpressionOperator value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.ExpressionOperator
                .builder()
                .enabled(value.getEnabled())
                .op(map(value.getOp()))
                .children(mapList(value.getChildren(), MappingUtil::map))
                .build();
    }

    public static stroom.query.api.ExpressionOperator.Op map(stroom.legacy.model_6_1.ExpressionOperator.Op value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.ExpressionOperator.Op.valueOf(value.name());
    }

    public static stroom.query.api.ExpressionTerm map(stroom.legacy.model_6_1.ExpressionTerm value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.ExpressionTerm
                .builder()
                .enabled(value.getEnabled())
                .field(value.getField())
                .condition(map(value.getCondition()))
                .value(value.getValue())
                .docRef(map(value.getDocRef()))
                .build();
    }

    public static stroom.query.api.ExpressionTerm.Condition map(stroom.legacy.model_6_1.ExpressionTerm.Condition value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.ExpressionTerm.Condition.valueOf(value.name());
    }

    public static stroom.dashboard.shared.Automate map(stroom.legacy.model_6_1.Automate value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.Automate(value.isOpen(), value.isRefresh(), value.getRefreshInterval());
    }

    public static Column map(stroom.legacy.model_6_1.Field value) {
        if (value == null) {
            return null;
        }

        return new Column(
                value.getId(),
                value.getName(),
                value.getExpression(),
                map(value.getSort()),
                map(value.getFilter()),
                map(value.getFormat()),
                value.getGroup(),
                value.getWidth(),
                value.isVisible(),
                value.isSpecial(),
                null,
                null);
    }

    public static ColumnRef mapColumnRef(stroom.legacy.model_6_1.Field value) {
        if (value == null) {
            return null;
        }

        return new ColumnRef(
                value.getId(),
                value.getName());
    }

    public static stroom.query.api.Sort map(stroom.legacy.model_6_1.Sort value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.Sort(
                value.getOrder(), map(value.getDirection()));
    }

    public static stroom.query.api.Sort.SortDirection map(stroom.legacy.model_6_1.Sort.SortDirection value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.Sort.SortDirection.valueOf(value.name());
    }

    public static IncludeExcludeFilter map(stroom.legacy.model_6_1.Filter value) {
        if (value == null) {
            return null;
        }

        return new IncludeExcludeFilter(value.getIncludes(), value.getExcludes());
    }

    public static stroom.query.api.Format map(stroom.legacy.model_6_1.Format value) {
        if (value == null) {
            return null;
        }

        stroom.query.api.FormatSettings formatSettings = null;
        if (stroom.legacy.model_6_1.Format.Type.NUMBER.equals(value.getType())) {
            if (value.getNumberFormat() != null) {
                final stroom.legacy.model_6_1.NumberFormat numberFormat = value.getNumberFormat();
                formatSettings = new stroom.query.api.NumberFormatSettings(numberFormat.getDecimalPlaces(),
                        numberFormat.getUseSeparator());
            }
        } else if (stroom.legacy.model_6_1.Format.Type.DATE_TIME.equals(value.getType())) {
            if (value.getDateTimeFormat() != null) {
                final stroom.legacy.model_6_1.DateTimeFormat dateTimeFormat = value.getDateTimeFormat();
                formatSettings = new stroom.query.api.DateTimeFormatSettings(false, dateTimeFormat.getPattern(),
                        map(dateTimeFormat.getTimeZone()));
            }
        }

        return new stroom.query.api.Format(map(value.getType()), formatSettings, false);
    }

    public static stroom.query.api.Format.Type map(stroom.legacy.model_6_1.Format.Type value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.Format.Type.valueOf(value.name());
    }

    public static UserTimeZone map(stroom.legacy.model_6_1.TimeZone value) {
        if (value == null) {
            return null;
        }

        return new UserTimeZone(map(value.getUse()),
                value.getId(),
                value.getOffsetHours(),
                value.getOffsetMinutes());
    }

    public static UserTimeZone.Use map(stroom.legacy.model_6_1.TimeZone.Use value) {
        if (value == null) {
            return null;
        }

        return UserTimeZone.Use.valueOf(value.name());
    }

    public static stroom.dashboard.shared.LayoutConfig map(stroom.legacy.model_6_1.LayoutConfig value) {
        if (value == null) {
            return null;
        }

        if (value instanceof stroom.legacy.model_6_1.SplitLayoutConfig) {
            final stroom.legacy.model_6_1.SplitLayoutConfig splitLayoutConfig = (stroom.legacy.model_6_1.SplitLayoutConfig) value;
            return new stroom.dashboard.shared.SplitLayoutConfig(
                    map(splitLayoutConfig.getPreferredSize()),
                    splitLayoutConfig.getDimension(),
                    mapList(splitLayoutConfig.getChildren(), MappingUtil::map));
        } else if (value instanceof stroom.legacy.model_6_1.TabLayoutConfig) {
            final stroom.legacy.model_6_1.TabLayoutConfig tabLayoutConfig = (stroom.legacy.model_6_1.TabLayoutConfig) value;
            return new stroom.dashboard.shared.TabLayoutConfig(
                    map(tabLayoutConfig.getPreferredSize()),
                    mapList(tabLayoutConfig.getTabs(), MappingUtil::map),
                    tabLayoutConfig.getSelected());
        }

        return null;
    }

    public static stroom.dashboard.shared.Size map(stroom.legacy.model_6_1.Size value) {
        if (value == null) {
            return null;
        }
        return new stroom.dashboard.shared.Size(value.getWidth(), value.getHeight());
    }

    public static stroom.dashboard.shared.TabConfig map(stroom.legacy.model_6_1.TabConfig value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.TabConfig(value.getId(), value.isVisible());
    }

    public static stroom.query.api.Query map(stroom.legacy.model_6_1.Query value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.Query(map(value.getDataSource()),
                map(value.getExpression()),
                mapList(value.getParams(), MappingUtil::map),
                null);
    }

    public static stroom.query.api.Param map(stroom.legacy.model_6_1.Param value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.Param(value.getKey(), value.getValue());
    }

    public static stroom.pipeline.shared.TextConverterDoc.TextConverterType map(stroom.legacy.model_6_1.TextConverter.TextConverterType value) {
        if (value == null) {
            return null;
        }

        return stroom.pipeline.shared.TextConverterDoc.TextConverterType.valueOf(value.name());
    }

    public static stroom.pipeline.shared.data.PipelineData map(stroom.legacy.model_6_1.PipelineData value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineData(
                map(value.getElements()),
                map(value.getProperties()),
                map(value.getPipelineReferences()),
                map(value.getLinks()),
                null);
    }

    public static stroom.pipeline.shared.data.PipelineElements map(stroom.legacy.model_6_1.PipelineElements value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineElements(
                mapList(value.getAdd(), MappingUtil::map),
                mapList(value.getRemove(), MappingUtil::map));
    }

    public static stroom.pipeline.shared.data.PipelineElement map(stroom.legacy.model_6_1.PipelineElement value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineElement(
                map(value.getElementType()),
                value.getId(),
                value.getType());
    }

    public static stroom.pipeline.shared.data.PipelineElementType map(stroom.legacy.model_6_1.PipelineElementType value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineElementType(
                value.getType(),
                map(value.getCategory()),
                value.getRoles().toArray(new String[0]),
                null);
    }

    public static stroom.pipeline.shared.data.PipelineElementType.Category map(stroom.legacy.model_6_1.PipelineElementType.Category value) {
        if (value == null) {
            return null;
        }

        return stroom.pipeline.shared.data.PipelineElementType.Category.valueOf(value.name());
    }


    public static stroom.pipeline.shared.data.PipelineProperties map(stroom.legacy.model_6_1.PipelineProperties value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineProperties(
                mapList(value.getAdd(), MappingUtil::map),
                mapList(value.getRemove(), MappingUtil::map));
    }

    public static stroom.pipeline.shared.data.PipelineProperty map(stroom.legacy.model_6_1.PipelineProperty value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineProperty(
                map(value.getPropertyType()),
                map(value.getSource()),
                value.getElement(),
                value.getName(),
                map(value.getValue()));
    }

    public static stroom.pipeline.shared.data.PipelinePropertyType map(stroom.legacy.model_6_1.PipelinePropertyType value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelinePropertyType(
                map(value.getElementType()),
                value.getName(),
                value.getType(),
                value.getDescription(),
                value.getDefaultValue(),
                value.isPipelineReference(),
                value.getDocRefTypes(),
                0);
    }

    public static stroom.pipeline.shared.data.PipelinePropertyValue map(stroom.legacy.model_6_1.PipelinePropertyValue value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelinePropertyValue(
                value.getString(),
                value.getInteger(),
                value.getLong(),
                value.isBoolean(),
                map(value.getEntity()));
    }

    public static stroom.pipeline.shared.data.PipelineReferences map(stroom.legacy.model_6_1.PipelineReferences value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineReferences(
                mapList(value.getAdd(), MappingUtil::map),
                mapList(value.getRemove(), MappingUtil::map));
    }

    public static stroom.pipeline.shared.data.PipelineReference map(stroom.legacy.model_6_1.PipelineReference value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineReference(
                value.getElement(),
                value.getName(),
                map(value.getPipeline()),
                map(value.getFeed()),
                value.getStreamType(),
                map(value.getSource()));
    }

    public static stroom.docref.DocRef map(stroom.legacy.model_6_1.SourcePipeline value) {
        if (value == null) {
            return null;
        }

        return map(value.getPipeline());
    }

    public static stroom.pipeline.shared.data.PipelineLinks map(stroom.legacy.model_6_1.PipelineLinks value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineLinks(
                mapList(value.getAdd(), MappingUtil::map),
                mapList(value.getRemove(), MappingUtil::map));
    }

    public static stroom.pipeline.shared.data.PipelineLink map(stroom.legacy.model_6_1.PipelineLink value) {
        if (value == null) {
            return null;
        }

        return new stroom.pipeline.shared.data.PipelineLink(
                map(value.getSource()),
                value.getFrom(),
                value.getTo());
    }

    public static List<LuceneIndexField> map(stroom.legacy.model_6_1.IndexFields value) {
        if (value == null) {
            return null;
        }

        return mapList(value.getIndexFields(), MappingUtil::map);
    }

    public static LuceneIndexField map(stroom.legacy.model_6_1.IndexField value) {
        if (value == null) {
            return null;
        }

        OldIndexFieldType indexFieldType = null;
        if (value.getFieldType() != null) {
            indexFieldType = OldIndexFieldType.valueOf(value.getFieldType().name());
        }
        final String fieldName = value.getFieldName();
        AnalyzerType analyzerType = null;
        if (value.getAnalyzerType() != null) {
            analyzerType = AnalyzerType.valueOf(value.getAnalyzerType().name());
        }
        final boolean indexed = value.isIndexed();
        final boolean stored = value.isStored();
        final boolean termPositions = value.isTermPositions();
        final boolean caseSensitive = value.isCaseSensitive();

        return new LuceneIndexField(
                fieldName,
                indexFieldType,
                null,
                null,
                analyzerType,
                indexed,
                stored,
                termPositions,
                caseSensitive);
    }

    public static stroom.processor.shared.QueryData map(stroom.legacy.model_6_1.QueryData value) {
        if (value == null) {
            return null;
        }

        return new stroom.processor.shared.QueryData(
                map(value.getDataSource()),
                map(value.getExpression()),
                null,
                null,
                map(value.getLimits()));
    }

    public static stroom.processor.shared.Limits map(stroom.legacy.model_6_1.Limits value) {
        if (value == null) {
            return null;
        }

        return new stroom.processor.shared.Limits(
                value.getStreamCount(),
                value.getEventCount(),
                value.getDurationMs());
    }

    public static stroom.data.retention.shared.DataRetentionRule map(stroom.legacy.model_6_1.DataRetentionRule value) {
        if (value == null) {
            return null;
        }

        return new stroom.data.retention.shared.DataRetentionRule(
                value.getRuleNumber(),
                value.getCreationTime(),
                value.getName(),
                value.isEnabled(),
                map(value.getExpression()),
                value.getAge(),
                map(value.getTimeUnit()),
                value.isForever());
    }

    public static TimeUnit map(stroom.legacy.model_6_1.TimeUnit value) {
        if (value == null) {
            return null;
        }

        return TimeUnit.valueOf(value.name());
    }
}
