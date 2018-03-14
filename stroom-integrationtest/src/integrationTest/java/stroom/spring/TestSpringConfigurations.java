package stroom.spring;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import stroom.datafeed.TestDataFeedServiceImplConfiguration;
import stroom.util.task.TaskScopeRunnable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class TestSpringConfigurations {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestSpringConfigurations.class);

    @Test
    public void testDependencies() {
        final Map<Class<?>, Optional<List<Class<?>>>> dependencies = getDependencies();

        // Test we don't import by more than one route.
        dependencies.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Class::getName)))
                .forEachOrdered(entry -> {
                    final Class<?> configuration = entry.getKey();
                    final Optional<List<Class<?>>> deps = entry.getValue();
                    LOGGER.info("Checking dependencies for " + configuration.getName());
                    traverseDeps(configuration, configuration, deps, dependencies, new HashMap<>());
                });
    }

    private void traverseDeps(final Class<?> configuration, final Class<?> owner, final Optional<List<Class<?>>> deps, final Map<Class<?>, Optional<List<Class<?>>>> dependencies, final Map<Class<?>, Class<?>> allDeps) {
        // Check all deps at this level.
        deps.ifPresent(set -> set
                .stream()
                .sorted((Comparator.comparing(Class::getName)))
                .forEach(dep -> {
                    if (!allDeps.containsKey(dep)) {
                        allDeps.put(dep, owner);
                    } else {
                        Assert.fail("Duplicate dependency for " + configuration.getName() + " -> " + dep.getName() + " via " + allDeps.get(dep).getName());
                    }
                }));

        // Now traverse into each dep.
        deps.ifPresent(set -> set
                .stream()
                .sorted((Comparator.comparing(Class::getName)))
                .forEach(dep -> {
                    traverseDeps(configuration, dep, dependencies.get(dep), dependencies, new HashMap<>(allDeps));
                }));
    }

    @Test
    public void printDependencies() throws IOException {
        // Check dependants first.
        final Map<Class<?>, Optional<List<Class<?>>>> dependencies = getDependencies();
        Map<Integer, Set<Class<?>>> dependencyDepths = new HashMap<>();
        dependencies.keySet().forEach(k -> {
            final int depth = getDepth(k, dependencies, 0);
            dependencyDepths.computeIfAbsent(depth, d -> new HashSet<>()).add(k);
        });

        try (final IuneraDependencyCsvWriter dependencyWriter = new IuneraDependencyCsvWriter(TestSpringConfigurations.class)) {

            final StringBuilder sb = new StringBuilder("\n");
            dependencyDepths
                    .keySet()
                    .stream()
                    .sorted(Comparator.reverseOrder())
                    .forEach(depth -> {
                        final Set<Class<?>> configurations = dependencyDepths.get(depth);
                        configurations
                                .stream()
                                .sorted(Comparator.comparing(Class::getName))
                                .forEach(configuration -> {
                                    sb.append("------------------------------------------------------------------------\n");
                                    sb.append("DEPENDENCIES FOR ");
                                    sb.append(configuration.getName());
                                    sb.append("\n");
                                    printTree(sb, configuration, dependencies, 0, dependencyWriter);
                                    sb.append("------------------------------------------------------------------------");
                                });
                    });

            LOGGER.info(sb.toString());
        }
    }

    private void printTree(final StringBuilder sb,
                           final Class<?> configuration,
                           final Map<Class<?>, Optional<List<Class<?>>>> dependencies,
                           final int depth,
                           final IuneraDependencyCsvWriter dependencyWriter) {
        final Optional<List<Class<?>>> deps = dependencies.get(configuration);
        for (int i = 0; i < depth; i++) {
            sb.append("\t");
        }
        if (depth > 0) {
            sb.append("|__ ");
        }

        sb.append(configuration.getName());
        sb.append("\n");
        deps.ifPresent(list -> list
                .stream()
                .sorted(Comparator.comparing(Class::getName))
                .forEach(dep -> {
                    printTree(sb, dep, dependencies, depth + 1, dependencyWriter);
                    dependencyWriter.write(configuration.getName(), dep.getName(), 1, 1);
                }));
    }

//    @Test
//    public void testRootConstruction() {
//        final Set<Class<?>> roots = new HashSet<>();
//        new FastClasspathScanner("stroom")
//                .matchClassesWithAnnotation(ComponentScan.class, c -> {
//                    roots.add(c);
//                })
//                .scan();
//
//        roots
//                .stream()
//                .sorted(Comparator.comparing(Class::getName))
//                .forEachOrdered(configuration -> {
//
////                    if (TestDataFeedServiceImplConfiguration.class.isAssignableFrom(configuration)) {
//
//                        final TaskScopeRunnable taskScopeRunnable = new TaskScopeRunnable(null) {
//                            @Override
//                            protected void exec() {
//                                try {
//                                    String message = "\n---------------------------------------------------\n" +
//                                            " Testing configuration " + configuration.getName() + "\n" +
//                                            "---------------------------------------------------";
//                                    LOGGER.info(message);
//
////                    System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD + ", Headless");
//                                    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configuration);
//                                    context.destroy();
//                                    LOGGER.info("Configuration OK " + configuration.getName());
//                                } catch (final Exception e) {
//                                    LOGGER.error(e.getMessage(), e);
//                                    Assert.fail("Error with configuration " + configuration.getName());
//                                }
//                            }
//                        };
//                        taskScopeRunnable.run();
////                    }
//                });
//    }

//    @Test
//    public void testConstruction() {
//        // Check dependants first.
//        final Map<Class<?>, Optional<List<Class<?>>>> dependencies = getDependencies();
//        Map<Integer, Set<Class<?>>> dependencyDepths = new HashMap<>();
//        dependencies.keySet().forEach(k -> {
//            final int depth = getDepth(k, dependencies, 0);
//            dependencyDepths.computeIfAbsent(depth, d -> new HashSet<>()).add(k);
//        });
//
//        dependencyDepths
//                .keySet()
//                .stream()
//                .sorted(Comparator.naturalOrder())
//                .forEach(depth -> {
//                    final Set<Class<?>> configurations = dependencyDepths.get(depth);
//                    configurations
//                            .stream()
//                            .sorted(Comparator.comparing(Class::getName))
//                            .forEach(configuration -> {
//
//                                final TaskScopeRunnable taskScopeRunnable = new TaskScopeRunnable(null) {
//                                    @Override
//                                    protected void exec() {
//                                        try {
//                                            LOGGER.info("Testing configuration " + configuration.getName());
//
////                    System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD + ", Headless");
//                                            final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configuration);
//                                            context.destroy();
//                                            LOGGER.info("Configuration OK " + configuration.getName());
//                                        } catch (final Exception e) {
//                                            LOGGER.error(e.getMessage(), e);
//                                            Assert.fail("Error with configuration " + configuration.getName());
//                                        }
//                                    }
//                                };
//                                taskScopeRunnable.run();
//
//                            });
//                });
//    }

    @Test
    public void testConstructionSummary() {
        // Check dependants first.
        final Map<Class<?>, Optional<List<Class<?>>>> dependencies = getDependencies();
        final Map<Integer, Set<Class<?>>> dependencyDepths = new HashMap<>();
        dependencies.keySet().forEach(k -> {
            final int depth = getDepth(k, dependencies, 0);
            dependencyDepths.computeIfAbsent(depth, d -> new HashSet<>()).add(k);
        });

        final Map<Class<?>, Exception> exceptions = new HashMap<>();
        final StringBuilder statusString = new StringBuilder();
        statusString.append("\n-----------------------------------------------------------------");
        statusString.append("\n SUMMARY FOR " + dependencies.size() + " SPRING CONFIGURATIONS");
        statusString.append("\n-----------------------------------------------------------------\n");

        dependencyDepths
                .keySet()
                .stream()
                .sorted(Comparator.naturalOrder())
                .forEach(depth -> {
                    final Set<Class<?>> configurations = dependencyDepths.get(depth);
                    configurations
                            .stream()
                            .sorted(Comparator.comparing(Class::getName))
                            .forEach(configuration -> {

                                final TaskScopeRunnable taskScopeRunnable = new TaskScopeRunnable(null) {
                                    @Override
                                    protected void exec() {
                                        try {
//                    System.setProperty("spring.profiles.active", StroomSpringProfiles.PROD + ", Headless");
                                            final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(configuration);
                                            context.destroy();

                                            statusString.append("\nOK    " + configuration.getName());
                                        } catch (final Exception e) {
                                            exceptions.put(configuration, e);
                                            LOGGER.debug(e.getMessage(), e);
                                            statusString.append("\nERROR  " + configuration.getName());
                                        }
                                    }
                                };
                                taskScopeRunnable.run();
                            });
                });


        LOGGER.info(statusString.toString());
//        Assert.assertEquals(0, exceptions.size());
    }

    private int getDepth(final Class<?> child, Map<Class<?>, Optional<List<Class<?>>>> dependencies, int depth) {
        final AtomicInteger maxDepth = new AtomicInteger(depth);

        // Check all deps at this level.
        final Optional<List<Class<?>>> deps = dependencies.get(child);
        // Now traverse into each dep.
        deps.ifPresent(set -> set.forEach(dep -> {
            int d = getDepth(dep, dependencies, depth + 1);
            if (d > maxDepth.get()) {
                maxDepth.set(d);
            }
        }));

        return maxDepth.get();
    }

    private Map<Class<?>, Optional<List<Class<?>>>> getDependencies() {
        final Map<Class<?>, Optional<List<Class<?>>>> dependencies = new HashMap<>();
        new FastClasspathScanner("stroom")
                .matchClassesWithAnnotation(Configuration.class, c -> {
                    try {
                        final Import anImport = c.getAnnotation(Import.class);
                        if (anImport != null) {
                            dependencies.computeIfAbsent(c, k -> Optional.of(Arrays.asList(anImport.value())));
                        }
                    } catch (final Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                    dependencies.computeIfAbsent(c, k -> Optional.empty());
                })
                .scan();
        return dependencies;
    }
}
