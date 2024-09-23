/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.language.functions;

import stroom.expression.api.DateTimeSettings;
import stroom.query.language.token.Param;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

abstract class AbstractTimeFunction extends AbstractFunction {

    private final ExpressionContext expressionContext;

    public AbstractTimeFunction(final ExpressionContext expressionContext, final String name) {
        super(name, 0, 0);
        this.expressionContext = expressionContext;
    }

    ZonedDateTime getReferenceTime() {
        final DateTimeSettings dateTimeSettings = expressionContext.getDateTimeSettings();
        Objects.requireNonNull(dateTimeSettings, "dateTimeSettings not set in searchRequest");
        Objects.requireNonNull(dateTimeSettings.getReferenceTime(),
                "referenceTime not set in searchRequest.dateTimeSettings");

        final Instant instant = Instant.ofEpochMilli(dateTimeSettings.getReferenceTime());
        switch (dateTimeSettings.getTimeZone().getUse()) {
            case LOCAL -> {
                final ZoneId zoneId = ZoneId.of(dateTimeSettings.getLocalZoneId());
                return ZonedDateTime.ofInstant(instant, zoneId);
            }
            case UTC -> {
                final ZoneId zoneId = ZoneId.of("Z");
                return ZonedDateTime.ofInstant(instant, zoneId);
            }
            case ID -> {
                final ZoneId zoneId = ZoneId.of(dateTimeSettings.getTimeZone().getId());
                return ZonedDateTime.ofInstant(instant, zoneId);
            }
            case OFFSET -> {
                final ZoneOffset zoneOffset = ZoneOffset
                        .ofHoursMinutes(dateTimeSettings.getTimeZone().getOffsetHours(),
                                dateTimeSettings.getTimeZone().getOffsetMinutes());
                return ZonedDateTime.ofInstant(instant, zoneOffset);
            }
            default -> {
                return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
            }
        }
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }

    @Override
    public boolean requiresChildData() {
        return false;
    }
}
