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

package stroom.query.impl;

import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.common.v2.ExpressionValidationException;
import stroom.query.common.v2.ExpressionValidator;
import stroom.query.shared.ExpressionResource;
import stroom.query.shared.ValidateExpressionRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.Objects;

@AutoLogged
public class ExpressionResourceImpl implements ExpressionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExpressionResourceImpl.class);

    @Override
    // No need to log a validation as it will be prior to the user actually doing the thing
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateExpressionResult validate(final ValidateExpressionRequest request) {
        Objects.requireNonNull(request);
        final ExpressionValidator expressionValidator = new ExpressionValidator(
                request.getFields(),
                request.getDateTimeSettings());
        try {
            expressionValidator.validate(request.getExpressionItem());
            return ValidateExpressionResult.ok();
        } catch (final ExpressionValidationException e) {
            return ValidateExpressionResult.failed(e);
        }
    }
}
