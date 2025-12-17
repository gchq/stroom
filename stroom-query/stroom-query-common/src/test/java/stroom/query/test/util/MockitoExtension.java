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

package stroom.query.test.util;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Parameter;

import static org.mockito.Mockito.mock;

public class MockitoExtension
        implements TestInstancePostProcessor, ParameterResolver {

    @Override
    public void postProcessTestInstance(final Object testInstance,
                                        final ExtensionContext context) {
        MockitoAnnotations.initMocks(testInstance);
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext,
                                     final ExtensionContext extensionContext) {
        return
                parameterContext.getParameter().isAnnotationPresent(Mock.class);
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext,
                                   final ExtensionContext extensionContext) {
        return getMock(parameterContext.getParameter(), extensionContext);
    }

    private Object getMock(
            final Parameter parameter, final ExtensionContext extensionContext) {

        final Class<?> mockType = parameter.getType();
        final Store mocks = extensionContext.getStore(Namespace.create(
                MockitoExtension.class, mockType));
        final String mockName = getMockName(parameter);

        if (mockName != null) {
            return mocks.getOrComputeIfAbsent(
                    mockName, key -> mock(mockType, mockName));
        } else {
            return mocks.getOrComputeIfAbsent(
                    mockType.getCanonicalName(), key -> mock(mockType));
        }
    }

    private String getMockName(final Parameter parameter) {
        final String explicitMockName = parameter.getAnnotation(Mock.class)
                .name().trim();
        if (!explicitMockName.isEmpty()) {
            return explicitMockName;
        } else if (parameter.isNamePresent()) {
            return parameter.getName();
        }
        return null;
    }
}
