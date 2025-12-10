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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.xml.converter.ds3.ref.VarMap;

public abstract class Expression extends Node {
    private final ExpressionFactory factory;
    private int matchCount;

    public Expression(final VarMap varMap, final ExpressionFactory factory) {
        super(varMap, factory);
        this.factory = factory;
    }

    public abstract void setInput(final CharSequence cs);

    public void resetMatchCount() {
        matchCount = 0;
    }

    public void incrementMatchCount() {
        matchCount++;
    }

    public int getMatchCount() {
        return matchCount;
    }

    public boolean checkMaxMatch() {
        return factory.getMaxMatch() == -1 || matchCount < factory.getMaxMatch();
    }

    public boolean checkMinMatch() {
        return factory.getMinMatch() == -1 || matchCount >= factory.getMinMatch();
    }

    public boolean checkOnlyMatch(final int parentMatchCount) {
        return factory.getOnlyMatch() == null ||
                factory.getOnlyMatch().contains(parentMatchCount);
    }

    public int getAdvance() {
        return factory.getAdvance();
    }

    public abstract Match match();

    @Override
    public boolean isExpression() {
        return true;
    }

    @Override
    public void clear() {
        super.clear();
        resetMatchCount();
    }
}
