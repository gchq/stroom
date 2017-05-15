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

package stroom.feed.server;

import org.springframework.context.annotation.Scope;
import stroom.feed.shared.FetchSupportedEncodingsAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@TaskHandlerBean(task = FetchSupportedEncodingsAction.class)
@Scope(value = StroomScope.TASK)
public class FetchSupportedEncodingsActionHandler
        extends AbstractTaskHandler<FetchSupportedEncodingsAction, SharedList<SharedString>> {
    private static final SharedList<SharedString> SUPPORTED_ENCODINGS;

    static {
        final List<String> list = new ArrayList<>();
        list.add("UTF-8");
        list.add("UTF-16LE");
        list.add("UTF-16BE");
        list.add("UTF-32LE");
        list.add("UTF-32BE");
        list.add("ASCII");
        list.add("");

        for (final String charset : Charset.availableCharsets().keySet()) {
            list.add(charset);
        }

        SUPPORTED_ENCODINGS = SharedList.convert(list);
    }

    @Override
    public SharedList<SharedString> exec(final FetchSupportedEncodingsAction action) {
        return SUPPORTED_ENCODINGS;
    }
}
