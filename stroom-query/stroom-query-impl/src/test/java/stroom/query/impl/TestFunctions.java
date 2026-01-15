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

import stroom.query.shared.CompletionItem;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

class TestFunctions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFunctions.class);

    @Test
    void testBuildFunctionCompletions() {
        final Functions functions = new Functions(UiConfig::new);
        final List<CompletionItem> completionItems = functions.buildFunctionCompletions()
                .stream()
                .sorted(Comparator.comparing(CompletionItem::getCaption))
                .toList();

        final String asciiTable = AsciiTable.fromCollection(completionItems);
        LOGGER.info("\n{}", asciiTable);
    }
}
