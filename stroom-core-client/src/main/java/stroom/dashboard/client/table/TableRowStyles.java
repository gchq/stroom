package stroom.dashboard.client.table;

import stroom.dashboard.client.table.cf.ConditionalFormattingDynamicStyles;
import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.TextAttributes;
import stroom.query.client.presenter.TableRow;
import stroom.security.client.presenter.ClassNameBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.RowStyles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TableRowStyles implements RowStyles<TableRow> {

    private final UserPreferencesManager userPreferencesManager;
    private Map<String, ConditionalFormattingRule> conditionalFormattingRules = new HashMap<>();

    public TableRowStyles(final UserPreferencesManager userPreferencesManager) {
        this.userPreferencesManager = userPreferencesManager;
    }

    @Override
    public String getStyleNames(final TableRow row, final int rowIndex) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        if (row != null && row.getMatchingRule() != null && !userPreferencesManager.isHideConditionalStyles()) {
            final ConditionalFormattingRule rule = conditionalFormattingRules.get(row.getMatchingRule());
            if (rule != null) {
                // Fixed styles.
                if (rule.getFormattingType() == null ||
                    ConditionalFormattingType.CUSTOM.equals(rule.getFormattingType())) {
                    classNameBuilder.addClassName(ConditionalFormattingDynamicStyles.create(rule.getCustomStyle()));
                } else if (ConditionalFormattingType.TEXT.equals(rule.getFormattingType())) {
                    classNameBuilder.addClassName(ConditionalFormattingSwatchUtil.CF_TEXT);
                    classNameBuilder.addClassName(rule.getFormattingStyle().getCssClassName());
                } else if (ConditionalFormattingType.BACKGROUND.equals(rule.getFormattingType())) {
                    classNameBuilder.addClassName(rule.getFormattingStyle().getCssClassName());
                }

                final TextAttributes textAttributes = rule.getTextAttributes();
                classNameBuilder.addClassName(ConditionalFormattingSwatchUtil
                        .getTextAttributeClassNames(textAttributes));
            }
        }

        return classNameBuilder.build();
    }

    public void setConditionalFormattingRules(final List<ConditionalFormattingRule> rules) {
        conditionalFormattingRules = new HashMap<>();
        if (rules != null) {
            for (final ConditionalFormattingRule rule : rules) {
                final ConditionalFormattingRule existing = conditionalFormattingRules.put(rule.getId(), rule);
                if (existing != null) {
                    GWT.log("Duplicate conditional formatting rule id");
                }
            }
        }
    }
}
