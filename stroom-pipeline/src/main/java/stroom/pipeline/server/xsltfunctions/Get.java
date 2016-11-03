/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.xsltfunctions;

import javax.annotation.Resource;

import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

@Component
@Scope(StroomScope.PROTOTYPE)
public class Get extends StroomExtensionFunctionCall {
    @Resource
    private TaskScopeMap map;

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        final String key = getSafeString(functionName, context, arguments, 0);
        final String value = map.get(key);

        if (value == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(value);
    }
}
