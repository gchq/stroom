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

package stroom.meta.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.MetaFields;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.UserTimeZone;
import stroom.query.api.UserTimeZone.Use;
import stroom.query.language.functions.FieldIndex;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestMetaServiceImpl {

    @Mock
    private MetaDao metaDao;
    @Captor
    private ArgumentCaptor<ExpressionCriteria> criteriaCaptor;

    @Test
    void searchUsesTheTimeZoneOfTheQuery() {
        final ExpressionCriteria criteria = new ExpressionCriteria(ExpressionOperator.builder()
                .addTerm(MetaFields.PARENT_CREATE_TIME.getFldName(), Condition.GREATER_THAN, "day()")
                .build());
        final DateTimeSettings dateTimeSettings = DateTimeSettings.builder()
                .referenceTime(Instant.parse("2024-07-01T09:00:00Z").toEpochMilli())
                .timeZone(UserTimeZone.builder().use(Use.ID).id("Europe/London").build())
                .build();

        new MetaServiceImpl(
                metaDao,
                null,
                null,
                null,
                null,
                null,
                null,
                Optional.empty(),
                MockSecurityContext.getInstance(),
                null,
                null,
                null,
                null,
                null)
                .search(criteria, new FieldIndex(), dateTimeSettings, null, null);

        Mockito.verify(metaDao).search(criteriaCaptor.capture(), Mockito.any(), Mockito.any());
        assertThat(ExpressionUtil.values(criteriaCaptor.getValue().getExpression()))
                .containsExactly("2024-06-30T23:00:00.000Z");
    }
}
