package stroom.dropwizard.common;

import stroom.util.HasAdminTasks;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.dropwizard.core.setup.Environment;
import io.dropwizard.servlets.tasks.Task;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AdminTasks {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AdminTasks.class);

    private final Environment environment;
    private final Set<HasAdminTasks> hasAdminTasksSet;

    @Inject
    public AdminTasks(final Environment environment,
                      final Set<HasAdminTasks> hasAdminTasksSet) {
        this.environment = environment;
        this.hasAdminTasksSet = hasAdminTasksSet;
    }

    public void register() {
        final List<Task> tasks = NullSafe.stream(hasAdminTasksSet)
                .filter(Objects::nonNull)
                .map(HasAdminTasks::getTasks)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Task::getName))
                .toList();

        LOGGER.info("Registering admin tasks");
        for (final Task task : tasks) {
            final String name = task.getName();
            LOGGER.info("\t{}", name);
            environment.admin().addTask(task);
        }
    }
}
