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

package stroom.query.language.functions;

import stroom.query.api.DateTimeSettings;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.Objects;

abstract class AbstractDateTimeFunction extends AbstractFunction {

    private final ExpressionContext expressionContext;
    final ZoneId zoneId;

    public AbstractDateTimeFunction(final ExpressionContext expressionContext,
                                    final String name,
                                    final int minParams,
                                    final int maxParams) {
        super(name, minParams, maxParams);
        this.expressionContext = expressionContext;
        zoneId = getZoneId();
    }

    private ZoneId getZoneId() {
        final DateTimeSettings dateTimeSettings = expressionContext.getDateTimeSettings();
        Objects.requireNonNull(dateTimeSettings, "dateTimeSettings not set in searchRequest");
        return UserTimeZoneUtil.getZoneId(dateTimeSettings);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }
}
