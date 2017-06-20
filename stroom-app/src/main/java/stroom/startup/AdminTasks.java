package stroom.startup;

import io.dropwizard.servlets.tasks.LogConfigurationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminTasks {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminTasks.class);

    private AdminTasks() {
    }

    public static void registerAdminTasks(final io.dropwizard.setup.Environment environment) {
        registerLogConfiguration(environment);
    }

    private static void registerLogConfiguration(io.dropwizard.setup.Environment environment) {

        //Task to allow configuration of log levels at runtime
        String path = environment.getAdminContext().getContextPath();

        //To change the log level do one of:
        //curl -X POST -d "logger=stroom&level=DEBUG" [admin context path]/tasks/log-level
        //http -f POST [admin context path]/tasks/log-level logger=stroom level=DEBUG
        //'http' requires installing HTTPie

        LOGGER.info("Registering Log Configuration Task on {}/tasks/log-level", path);
        environment.admin()
                .addTask(new LogConfigurationTask());
    }
}
