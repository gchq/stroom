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

package stroom.util;


import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for command line tools that handles setting a load of args on the
 * program as name value pairs.
 */
public abstract class AbstractCommandLineTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractCommandLineTool.class);

    private Map<String, String> map;
    private List<String> validArguments;
    private int maxPropLength = 0;

    public static void main(final String[] args) {
        final Example example = new Example();
        example.doMain(args);
    }

    public abstract void run();

    protected void checkArgs() {
    }

    protected void failArg(final String arg, final String msg) {
        throw new RuntimeException(LogUtil.message("Argument '{}' - {}", arg, msg));
    }

    protected void failMissingMandatoryArg(final String arg) {
        throw new RuntimeException(LogUtil.message("Argument '{}' must be supplied", arg));
    }

    public void init(final String[] args) {
        try {
            map = ArgsUtil.parse(args);
            validArguments = new ArrayList<>();

            LOGGER.debug("Arguments:\n{}", map.entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + " = " + entry.getValue())
                    .collect(Collectors.joining("\n")));

            final BeanInfo beanInfo = Introspector.getBeanInfo(this.getClass());

            for (final PropertyDescriptor field : beanInfo.getPropertyDescriptors()) {
                if (field.getWriteMethod() != null) {
                    if (field.getName().length() > maxPropLength) {
                        maxPropLength = field.getName().length();
                    }
                    if (map.containsKey(field.getName())) {
                        validArguments.add(field.getName());
                        field.getWriteMethod().invoke(this, getAsType(field));
                    }
                }
            }

            checkArgs();
        } catch (final IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Object getAsType(final PropertyDescriptor descriptor) {
        final Class<?> propertyClass = descriptor.getPropertyType();
        if (propertyClass.equals(String.class)) {
            return map.get(descriptor.getName());
        }
        if (propertyClass.equals(Boolean.class) || propertyClass.equals(Boolean.TYPE)) {
            return Boolean.parseBoolean(map.get(descriptor.getName()));
        }
        if (propertyClass.equals(Integer.class) || propertyClass.equals(Integer.TYPE)) {
            return Integer.parseInt(map.get(descriptor.getName()));
        }
        if (propertyClass.equals(Long.class) || propertyClass.equals(Long.TYPE)) {
            return Long.parseLong(map.get(descriptor.getName()));
        }
        throw new RuntimeException("AbstractCommandLineTool does not know about properties of type " + propertyClass);
    }

    public void doMain(final String[] args) {
        init(args);
        run();
    }

    public void traceArguments(final PrintStream printStream) {
        try {
            doTraceArguments(printStream);
        } catch (final RuntimeException e) {
            printStream.println(e.getMessage());
        }
    }

    private void doTraceArguments(final PrintStream printStream) {
        try {
            final BeanInfo beanInfo = Introspector.getBeanInfo(this.getClass());
            for (final PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
                // Only do properties with getters
                if (pd.getWriteMethod() != null) {
                    // Simple getter ?
                    String suffix = " (default)";
                    if (map.containsKey(pd.getName())) {
                        suffix = " (arg)";
                    }
                    String value = "";

                    if (pd.getReadMethod() != null && pd.getReadMethod().getParameterTypes().length == 0) {
                        value = String.valueOf(pd.getReadMethod().invoke(this));
                    } else {
                        // No simple getter
                        Field field = null;
                        try {
                            field = this.getClass().getDeclaredField(pd.getName());
                        } catch (final NoSuchFieldException nsfex) {
                            // Ignore
                        }
                        if (field != null) {
                            field.setAccessible(true);
                            value = String.valueOf(field.get(this));
                        } else {
                            value = "?";

                        }
                    }
                    printStream.println(Strings.padEnd(pd.getName(), maxPropLength, ' ') + " = " + value + suffix);
                }
            }
        } catch (final IntrospectionException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    // --------------------------------------------------------------------------------


    private static class Example extends AbstractCommandLineTool {

        @Override
        public void run() {
            throw new RuntimeException();
        }
    }

}
