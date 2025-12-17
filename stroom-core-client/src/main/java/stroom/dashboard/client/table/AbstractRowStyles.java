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

package stroom.dashboard.client.table;

import stroom.dashboard.client.table.cf.ConditionalFormattingDynamicStyles;
import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.TextAttributes;
import stroom.security.client.presenter.ClassNameBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.RowStyles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AbstractRowStyles<T> implements RowStyles<T> {

    private final UserPreferencesManager userPreferencesManager;
    private Map<String, ConditionalFormattingRule> conditionalFormattingRules = new HashMap<>();
    private final Function<T, String> ruleIdFunction;

    public AbstractRowStyles(final UserPreferencesManager userPreferencesManager,
                             final Function<T, String> ruleIdFunction) {
        this.userPreferencesManager = userPreferencesManager;
        this.ruleIdFunction = ruleIdFunction;
    }

    @Override
    public String getStyleNames(final T row, final int rowIndex) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        final String ruleId = ruleIdFunction.apply(row);
        if (ruleId != null && !userPreferencesManager.isHideConditionalStyles()) {
            final ConditionalFormattingRule rule = conditionalFormattingRules.get(ruleId);
            if (rule != null) {
                // Fixed styles.
                if (rule.getFormattingType() == null ||
                    ConditionalFormattingType.CUSTOM.equals(rule.getFormattingType())) {
                    classNameBuilder.addClassName(ConditionalFormattingDynamicStyles.create(rule.getCustomStyle()));
                } else if (ConditionalFormattingType.TEXT.equals(rule.getFormattingType())) {
                    classNameBuilder.addClassName(ConditionalFormattingSwatchUtil.CF_TEXT)
                            .addClassName(rule.getFormattingStyle().getCssClassName());
                } else if (ConditionalFormattingType.BACKGROUND.equals(rule.getFormattingType())) {
                    classNameBuilder.addClassName(rule.getFormattingStyle().getCssClassName());
                }

                final TextAttributes textAttributes = rule.getTextAttributes();
                classNameBuilder.addAll(ConditionalFormattingSwatchUtil.getTextAttributeClassNames(textAttributes));
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
