package stroom.util;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import stroom.spring.PersistenceConfiguration;
import stroom.spring.ScopeConfiguration;
import stroom.spring.ServerConfiguration;

@Configuration
@Import({
        ScopeConfiguration.class,
        PersistenceConfiguration.class,
        ServerConfiguration.class
//        ,
//        HeadlessConfiguration.class
})
public class ToolSpringConfiguration {
}
