package stroom.startup;

import io.dropwizard.servlets.tasks.LogConfigurationTask;

public class AdminTasks {

    private AdminTasks() {
    }

    public static void registerAdminTasks(final io.dropwizard.setup.Environment environment) {

        //Task to allow configuration of log levels at runtime
        environment.admin().addTask(new LogConfigurationTask());
    }
}
