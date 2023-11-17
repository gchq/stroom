package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.query.shared.QueryHelpField;
import stroom.query.shared.QueryHelpRow;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class FieldDetailProvider implements DetailProvider {

    private static final String DETAIL_BASE_CLASS = "queryHelpDetail";
    private static final String DETAIL_TABLE_CLASS = DETAIL_BASE_CLASS + "-table";
    private static final String DETAIL_DESCRIPTION_CLASS = DETAIL_BASE_CLASS + "-description";

    @Override
    public void getDetail(final QueryHelpRow row, final Consumer<Detail> consumer) {
        final InsertType insertType = GwtNullSafe.isBlankString(row.getTitle())
                ? InsertType.BLANK
                : InsertType.PLAIN_TEXT;
        final String insertText = GwtNullSafe.get(row.getTitle(), this::decorateFieldName);
        final HtmlBuilder documentation = new HtmlBuilder();
        documentation.div(hb1 -> {
            hb1.bold(hb2 -> hb2.append(row.getTitle()));
            hb1.br();
            hb1.hr();

            final QueryHelpField field = (QueryHelpField) row.getData();
            hb1.para(hb2 -> hb2.append(buildFieldDetails(field.getFieldInfo())),
                    Attribute.className(DETAIL_DESCRIPTION_CLASS));
        }, Attribute.className(DETAIL_BASE_CLASS));
        final Detail detail = new Detail(insertType, insertText, documentation.toSafeHtml());
        consumer.accept(detail);
    }

    private String decorateFieldName(final String fieldName) {
        return GwtNullSafe.get(fieldName, str ->
                "${" + str + "}");
    }

    private SafeHtml buildFieldDetails(final FieldInfo fieldInfo) {
        final String fieldName = fieldInfo.getFieldName();
        final String fieldType = fieldInfo.getFieldType().getDisplayValue();
        final String supportedConditions = fieldInfo.getConditions().toString();

        final HtmlBuilder htmlBuilder = HtmlBuilder.builder();
        appendKeyValueTable(htmlBuilder,
                Arrays.asList(
                        new SimpleEntry<>("Name:", fieldName),
                        new SimpleEntry<>("Type:", fieldType),
                        new SimpleEntry<>("Supported Conditions:", supportedConditions),
                        new SimpleEntry<>("Is queryable:", asDisplayValue(fieldInfo.queryable())),
                        new SimpleEntry<>("Is numeric:", asDisplayValue(fieldInfo.getFieldType().isNumeric()))));
        return htmlBuilder.toSafeHtml();
    }

    private void appendKeyValueTable(final HtmlBuilder htmlBuilder,
                                     final List<Entry<String, String>> entries) {

        final TableBuilder tableBuilder = new TableBuilder();
        for (final Entry<String, String> entry : entries) {
            tableBuilder.row(
                    HtmlBuilder.builder()
                            .bold(htmlBuilder2 -> htmlBuilder2.append(entry.getKey()))
                            .toSafeHtml(),
                    SafeHtmlUtil.from(entry.getValue()));
        }
        htmlBuilder.div(tableBuilder::write, Attribute.className(DETAIL_TABLE_CLASS));
    }

    private String asDisplayValue(final boolean bool) {
        return bool
                ? "True"
                : "False";
    }
}
