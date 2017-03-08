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

package stroom.dashboard.expression;

public abstract class AbstractSingleChildGenerator extends AbstractGenerator {
    private static final long serialVersionUID = 513621715143449935L;

    final Generator childGenerator;

    public AbstractSingleChildGenerator(final Generator childGenerator) {
        this.childGenerator = childGenerator;
    }

    @Override
    public void addChildKey(final Object key) {
        childGenerator.addChildKey(key);
    }

    @Override
    public abstract void set(String[] values);

    @Override
    public abstract Object eval();

    @Override
    public void merge(final Generator generator) {
        addChildren((AbstractSingleChildGenerator) generator);
    }

    public void addChildren(final AbstractSingleChildGenerator generator) {
        childGenerator.merge(generator.childGenerator);
    }
}
