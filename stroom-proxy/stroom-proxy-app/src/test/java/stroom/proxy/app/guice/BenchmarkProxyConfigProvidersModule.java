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

package stroom.proxy.app.guice;

import stroom.util.logging.LogUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Provides;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BenchmarkProxyConfigProvidersModule {

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // All this stuff down here is for testing the performance of different ways of
    // providing config to classes that need it.
    // Method 1 - Current approach of the provider looking up the config in a hashmap
    // Method 2 - New idea of having a load of generated volatile variables with getters
    //            and setters to avoid hash lookup.
    // Method 3 - Don't use a guice provider as a control.
    // In summary method2 is twice as fast as method1 but method 1 is still doing 7mil/s
    // so plenty fast enough.
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @Fork(value = 1, warmups = 1)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Measurement(iterations = 1)
    public void benchProviderMethod1(final ExecutionPlan plan) {
        final MyPojo myPojo = plan.provider.get();
        if (!"foo".equals(myPojo.value)) {
            throw new RuntimeException(LogUtil.message("Invalid value"));
        }
    }

    @State(Scope.Benchmark)
    public static class ExecutionPlan {

        @Param({ "1", "2", "3"})
        public int methodNo;

        public Provider<MyPojo> provider;

        @Setup(Level.Invocation)
        public void setUp() {
            final AbstractModule module;
            if (methodNo == 1) {
                // Config instances are looked up in a hashmap
                module = new MyModule1();
            } else if (methodNo == 2) {
                // Config instances are directly read from a variable
                module = new MyModule2();
            } else if (methodNo == 3) {
                // No guice involvement, just a noddy lambda. As close as we can get to simulating
                // guice injecting a
                // config instance with no provider
                module = null;
            } else {
                throw new RuntimeException(LogUtil.message("Bad methodNo {}", methodNo));
            }
            if (methodNo == 3) {
                final MyPojo myPojo = new MyPojo("foo");
                provider = () -> myPojo;
            } else {
                final Injector injector = Guice.createInjector(module);

                if (methodNo == 2) {
                    final ConfigSetter configSetter = injector.getInstance(ConfigSetter.class);
                    configSetter.setMyConfig(new MyPojo("foo"));
                }
                provider = injector.getProvider(MyPojo.class);
            }
        }
    }

    private static class MyModule1 extends AbstractModule {

        @Override
        protected void configure() {
            bind(MyConfigMapper.class).asEagerSingleton();
        }

        @Provides
        public MyPojo getMyConfig(final MyConfigMapper myConfigMapper) {
            return myConfigMapper.getConfigObject(MyPojo.class);
        }
    }

    private interface ConfigSetter {
        void setMyConfig(final MyPojo myPojo);
    }

    private static class MyModule2 extends AbstractModule implements ConfigSetter {

        private volatile MyPojo myPojo;

        @Override
        protected void configure() {
            bind(MyConfigMapper.class).asEagerSingleton();
            bind(ConfigSetter.class).toInstance(this);
        }

        @Provides
        public MyPojo getMyConfig() {
            return Objects.requireNonNull(myPojo, "No MyConfig found");
        }

        @Override
        public void setMyConfig(final MyPojo myPojo) {
            // Do we care if this is not done under lock?
            this.myPojo = myPojo;
        }
    }

    private static class MyConfigMapper {
        private volatile Map<Class<?>, Object> map = new HashMap<>();

        public MyConfigMapper() {
            this.map = new HashMap<>(Map.of(MyPojo.class, new MyPojo("foo")));
        }

        <T> T getConfigObject(final Class<T> clazz) {
            final Object config = map.get(clazz);
            Objects.requireNonNull(config, "No config instance found for class " + clazz.getName());
            try {
                return clazz.cast(config);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message(
                        "Error casting config object to {}, found {}",
                        clazz.getName(),
                        config.getClass().getName()), e);
            }
        }
    }

    private static class MyPojo {
        private final String value;

        private MyPojo(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
