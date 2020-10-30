package stroom.legacy.impex_6_1;

import stroom.index.shared.AnalyzerType;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldType;

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
        return new stroom.dashboard.shared.DashboardConfig(value.getParameters(), mapList(value.getComponents(), MappingUtil::map), map(value.getLayout()), map(value.getTabVisibility()));
    }

    public static stroom.dashboard.shared.ComponentConfig map(stroom.legacy.model_6_1.ComponentConfig value) {
        if (value == null) {
            return null;
        }
        return new stroom.dashboard.shared.ComponentConfig(value.getType(), value.getId(), value.getName(), map(value.getSettings()));
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

        return new stroom.dashboard.shared.QueryComponentSettings(map(value.getDataSource()), map(value.getExpression()), map(value.getAutomate()));
    }

    public static stroom.dashboard.shared.TableComponentSettings map(stroom.legacy.model_6_1.TableComponentSettings value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.TableComponentSettings(
                value.getQueryId(),
                mapList(value.getFields(), MappingUtil::map),
                value.getExtractValues(),
                map(value.getExtractionPipeline()),
                value.getMaxResults(),
                value.getShowDetail(),
                mapList(value.getConditionalFormattingRules(), MappingUtil::map),
                value.getModelVersion());
    }

    public static stroom.dashboard.shared.ConditionalFormattingRule map(stroom.legacy.model_6_1.ConditionalFormattingRule value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.ConditionalFormattingRule(
                value.getId(),
                map(value.getExpression()),
                value.isHide(),
                value.getBackgroundColor(),
                value.getTextColor(),
                value.isEnabled());
    }

    public static stroom.dashboard.shared.VisComponentSettings map(stroom.legacy.model_6_1.VisComponentSettings value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.VisComponentSettings(
                value.getTableId(),
                map(value.getVisualisation()),
                value.getJSON(),
                map(value.getTableSettings()));
    }

    public static stroom.dashboard.shared.TextComponentSettings map(stroom.legacy.model_6_1.TextComponentSettings value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.TextComponentSettings(
                value.getTableId(),
                map(value.getStreamIdField()),
                map(value.getPartNoField()),
                map(value.getRecordNoField()),
                map(value.getLineFromField()),
                map(value.getColFromField()),
                map(value.getLineToField()),
                map(value.getColToField()),
                map(value.getPipeline()),
                value.isShowAsHtml(),
                value.isShowStepping(),
                value.getModelVersion());
    }

    public static stroom.query.api.v2.ExpressionItem map(stroom.legacy.model_6_1.ExpressionItem value) {
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

    public static stroom.query.api.v2.ExpressionOperator map(stroom.legacy.model_6_1.ExpressionOperator value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.v2.ExpressionOperator(value.getEnabled(), map(value.getOp()), mapList(value.getChildren(), MappingUtil::map));
    }

    public static stroom.query.api.v2.ExpressionOperator.Op map(stroom.legacy.model_6_1.ExpressionOperator.Op value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.v2.ExpressionOperator.Op.valueOf(value.name());
    }

    public static stroom.query.api.v2.ExpressionTerm map(stroom.legacy.model_6_1.ExpressionTerm value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.v2.ExpressionTerm(value.getEnabled(), value.getField(), map(value.getCondition()), value.getValue(), map(value.getDocRef()));
    }

    public static stroom.query.api.v2.ExpressionTerm.Condition map(stroom.legacy.model_6_1.ExpressionTerm.Condition value) {
        if (value == null) {
            return null;
        }

        return stroom.query.api.v2.ExpressionTerm.Condition.valueOf(value.name());
    }

    public static stroom.dashboard.shared.Automate map(stroom.legacy.model_6_1.Automate value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.Automate(value.isOpen(), value.isRefresh(), value.getRefreshInterval());
    }

    public static stroom.dashboard.shared.Field map(stroom.legacy.model_6_1.Field value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.Field(
                value.getId(),
                value.getName(),
                value.getExpression(),
                map(value.getSort()),
                map(value.getFilter()),
                map(value.getFormat()),
                value.getGroup(),
                200,
                true,
                false);
    }

    public static stroom.dashboard.shared.Sort map(stroom.legacy.model_6_1.Sort value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.Sort(
                value.getOrder(), map(value.getDirection()));
    }

    public static stroom.dashboard.shared.Sort.SortDirection map(stroom.legacy.model_6_1.Sort.SortDirection value) {
        if (value == null) {
            return null;
        }

        return stroom.dashboard.shared.Sort.SortDirection.valueOf(value.name());
    }

    public static stroom.dashboard.shared.Filter map(stroom.legacy.model_6_1.Filter value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.Filter(value.getIncludes(), value.getExcludes());
    }

    public static stroom.dashboard.shared.Format map(stroom.legacy.model_6_1.Format value) {
        if (value == null) {
            return null;
        }

        stroom.dashboard.shared.FormatSettings formatSettings = null;
        if (stroom.legacy.model_6_1.Format.Type.NUMBER.equals(value.getType())) {
            if (value.getNumberFormat() != null) {
                final stroom.legacy.model_6_1.NumberFormat numberFormat = value.getNumberFormat();
                formatSettings = new stroom.dashboard.shared.NumberFormatSettings(numberFormat.getDecimalPlaces(), numberFormat.getUseSeparator());
            }
        } else if (stroom.legacy.model_6_1.Format.Type.DATE_TIME.equals(value.getType())) {
            if (value.getDateTimeFormat() != null) {
                final stroom.legacy.model_6_1.DateTimeFormat dateTimeFormat = value.getDateTimeFormat();
                formatSettings = new stroom.dashboard.shared.DateTimeFormatSettings(dateTimeFormat.getPattern(), map(dateTimeFormat.getTimeZone()));
            }
        }

        return new stroom.dashboard.shared.Format(map(value.getType()), formatSettings, false);
    }

    public static stroom.dashboard.shared.Format.Type map(stroom.legacy.model_6_1.Format.Type value) {
        if (value == null) {
            return null;
        }

        return stroom.dashboard.shared.Format.Type.valueOf(value.name());
    }

    public static stroom.dashboard.shared.TimeZone map(stroom.legacy.model_6_1.TimeZone value) {
        if (value == null) {
            return null;
        }

        return new stroom.dashboard.shared.TimeZone(map(value.getUse()), value.getId(), value.getOffsetHours(), value.getOffsetMinutes());
    }

    public static stroom.dashboard.shared.TimeZone.Use map(stroom.legacy.model_6_1.TimeZone.Use value) {
        if (value == null) {
            return null;
        }

        return stroom.dashboard.shared.TimeZone.Use.valueOf(value.name());
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

    public static stroom.dashboard.shared.DashboardConfig.TabVisibility map(stroom.legacy.model_6_1.DashboardConfig.TabVisibility value) {
        if (value == null) {
            return null;
        }

        return stroom.dashboard.shared.DashboardConfig.TabVisibility.valueOf(value.name());
    }

    public static stroom.query.api.v2.Query map(stroom.legacy.model_6_1.Query value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.v2.Query(map(value.getDataSource()), map(value.getExpression()), mapList(value.getParams(), MappingUtil::map));
    }

    public static stroom.query.api.v2.Param map(stroom.legacy.model_6_1.Param value) {
        if (value == null) {
            return null;
        }

        return new stroom.query.api.v2.Param(value.getKey(), value.getValue());
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
                value.getIcon());
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

    public static List<stroom.index.shared.IndexField> map(stroom.legacy.model_6_1.IndexFields value) {
        if (value == null) {
            return null;
        }

        return mapList(value.getIndexFields(), MappingUtil::map);
    }

    public static stroom.index.shared.IndexField map(stroom.legacy.model_6_1.IndexField value) {
        if (value == null) {
            return null;
        }

        IndexFieldType indexFieldType = null;
        if (value.getFieldType() != null) {
            indexFieldType = IndexFieldType.valueOf(value.getFieldType().name());
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

        return new IndexField(
                indexFieldType,
                fieldName,
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
}
