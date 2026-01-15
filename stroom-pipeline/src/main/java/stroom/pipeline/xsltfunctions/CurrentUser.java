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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.state.CurrentUserHolder;
import stroom.security.api.UserIdentity;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

class CurrentUser extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "current-user";
    private static final Function<UserIdentity, String> DEFAULT_FUNC = UserIdentity::getUserIdentityForAudit;
    private static Map<String, Function<UserIdentity, String>> TYPE_TO_FUNC_MAP = Map.of(
            "display", DEFAULT_FUNC,
            "subject", UserIdentity::subjectId,
            "full", userIdentity -> userIdentity.getFullName().orElse(""));

    private final CurrentUserHolder currentUserHolder;

    @Inject
    CurrentUser(final CurrentUserHolder currentUserHolder) {
        this.currentUserHolder = currentUserHolder;
    }

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) throws XPathException {
        String result = null;

        final Function<UserIdentity, String> function;
        if (arguments == null || arguments.length == 0) {
            function = DEFAULT_FUNC;
        } else {
            final String type = getSafeString(functionName, context, arguments, 0);
            if (NullSafe.isBlankString(type)) {
                function = DEFAULT_FUNC;
            } else {
                function = Objects.requireNonNullElse(TYPE_TO_FUNC_MAP.get(type), DEFAULT_FUNC);
            }
        }

        try {
            final UserIdentity currentUser = currentUserHolder.getCurrentUser();
            result = function.apply(currentUser);
        } catch (final RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
