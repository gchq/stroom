package stroom.spring;

import com.google.common.base.Preconditions;
import com.google.inject.Provider;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.hibernate.jpa.boot.spi.IntegratorProvider;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Singleton
public class PersistServiceImpl implements Provider<EntityManager>, UnitOfWork, PersistService {
    private static final String PACKAGE = "stroom";

    private final DataSource dataSource;

    private volatile EntityManagerFactory emFactory;
    private final ThreadLocal<Deque<Context>> threadLocal = new ThreadLocal<>();

    @Inject
    PersistServiceImpl(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public EntityManager get() {
        final Context currentContext = getContext();

        Preconditions.checkState(
                null != currentContext,
                "Requested EntityManager outside work unit. "
                        + "Try calling UnitOfWork.begin() first, or use a PersistFilter if you "
                        + "are inside a servlet environment.");

        return currentContext.getEntityManager();
    }

    @Override
    public void begin() {
        final Deque<Context> deque = getDeque();

        final Context currentContext = getContext();

        Context context;
        if (currentContext != null) {
            context = new ChildContext(currentContext);
        } else {
            final EntityManager entityManager = emFactory.createEntityManager();
            context = new RootContext(entityManager);
        }
        deque.offerLast(context);
    }

    @Override
    public void end() {
        final Deque<Context> deque = getDeque();
        final Context context = deque.pollLast();

        Preconditions.checkState(context != null, "Attempt to pop context when there is no context");

        if (deque.peekLast() == null) {
            final EntityManager entityManager = context.getEntityManager();
            entityManager.close();
        }
    }


    @Override
    public synchronized void start() {
        Preconditions.checkState(null == emFactory, "Persistence service was already initialized.");
        this.emFactory = entityManagerFactory(dataSource);
    }

    @Override

    public synchronized void stop() {
        Preconditions.checkState(emFactory.isOpen(), "Persistence service was already shut down.");
        emFactory.close();
        emFactory = null;
    }

//    private EntityManager entityManager(final Provider<EntityManagerFactory> entityManagerFactoryProvider) {
//        final EntityManagerFactory entityManagerFactory = entityManagerFactoryProvider.get();
//        return entityManagerFactory.createEntityManager();
//    }

    private EntityManagerFactory entityManagerFactory(final DataSource dataSource) {
        final PersistenceUnitInfo persistenceUnitInfo = persistenceUnitInfo(getClass().getSimpleName(), dataSource);
        final Map<String, Object> configuration = new HashMap<>();
        final Integrator integrator = integrator();
        if (integrator != null) {
            configuration.put(
                    "hibernate.integrator_provider",
                    (IntegratorProvider) () ->
                            Collections.singletonList(integrator)
            );
        }

        return new HibernatePersistenceProvider().createContainerEntityManagerFactory(persistenceUnitInfo, configuration);


//
//
//        final Configuration configuration = new Configuration()
//////                .addAnnotatedClass(stroom.security.Permission.class)
//////        .addAnnotatedClass(stroom.security.AppPermission.class)
//////        .addAnnotatedClass(stroom.security.DocumentPermission.class)
//////        .addAnnotatedClass(stroom.security.User.class)
//////        .addAnnotatedClass(stroom.security.UserGroupUser.class)
////
////        .addAnnotatedClass(stroom.streamtask.shared.StreamProcessor.class)
////        .addAnnotatedClass(stroom.streamtask.shared.StreamProcessorFilter.class)
////        .addAnnotatedClass(stroom.streamtask.shared.StreamProcessorFilterTracker.class)
////        .addAnnotatedClass(stroom.streamtask.shared.StreamTask.class)
////
////        .addAnnotatedClass(stroom.node.shared.Volume.class)
////        .addAnnotatedClass(stroom.node.shared.VolumeState.class)
////
////        .addAnnotatedClass(stroom.streamstore.shared.Stream.class)
////        .addAnnotatedClass(stroom.streamstore.shared.StreamVolume.class)
////        .addAnnotatedClass(stroom.streamstore.shared.StreamAttributeKey.class)
////        .addAnnotatedClass(stroom.streamstore.shared.StreamAttributeValue.class)
////        .addAnnotatedClass(stroom.feed.shared.Feed.class)
////        .addAnnotatedClass(stroom.streamstore.shared.StreamType.class)
////
////        .addAnnotatedClass(stroom.jobsystem.shared.Job.class)
////        .addAnnotatedClass(stroom.jobsystem.shared.JobNode.class)
////        .addAnnotatedClass(stroom.jobsystem.shared.ClusterLock.class)
////
//////        .addAnnotatedClass(stroom.index.shared.Index.class)
//////        .addAnnotatedClass(stroom.index.shared.IndexShard.class)
//////
//////        .addAnnotatedClass(stroom.statistics.shared.StatisticStoreEntity.class)
//////        .addAnnotatedClass(stroom.stats.shared.StroomStatsStoreEntity.class)
////
////        .addAnnotatedClass(stroom.xmlschema.shared.XMLSchema.class)
////
//////        .addAnnotatedClass(stroom.visualisation.shared.Visualisation.class)
//////
//////        .addAnnotatedClass(stroom.script.shared.Script.class)
//////        .addAnnotatedClass(stroom.entity.shared.Res.class)
////
////        .addAnnotatedClass(stroom.pipeline.shared.PipelineEntity.class)
////
//////        .addAnnotatedClass(stroom.dashboard.shared.Dashboard.class)
//////
//////        .addAnnotatedClass(stroom.dashboard.shared.QueryEntity.class)
////
//////        addAnnotatedClass(stroom.stream.OldFolder)
////
////        .addAnnotatedClass(stroom.node.shared.Node.class)
////        .addAnnotatedClass(stroom.node.shared.Rack.class)
////
////        .addAnnotatedClass(stroom.node.shared.GlobalProperty.class)
////
////        .addAnnotatedClass(stroom.ruleset.shared.Policy.class)
//                .addPackage("stroom")
//            .setProperty("hibernate.dialect" , "org.hibernate.dialect.MySQLInnoDBDialect" )
//            .setProperty("hibernate.show_sql" , "true" )
//            .setProperty("hibernate.format_sql" , "true" )
//            .setProperty("hibernate.hbm2ddl.auto" , "validate" )
//                .setProperty("hibernate.connection.datasource", "java:/stroom");
//
//        // A SessionFactory is set up once for an application!
//        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
//                .applySettings(configuration.getProperties())
//                .build();
////
////        return configuration.buildSessionFactory(registry);
//
//        try {
//        return new MetadataSources(registry).buildMetadata().buildSessionFactory();
//        }        catch (Exception e) {
//            // The registry would be destroyed by the SessionFactory, but we had trouble building the SessionFactory
//            // so destroy it manually.
//            StandardServiceRegistryBuilder.destroy( registry );
//        }
//
//        throw new RuntimeException("Unable to create session factory");
    }

    private PersistenceUnitInfoImpl persistenceUnitInfo(final String name, final DataSource dataSource) {
        final List<String> entityClassNames = new ArrayList<>();
        new FastClasspathScanner(PACKAGE)
                .matchClassesWithAnnotation(Entity.class, classWithAnnotation -> entityClassNames.add(classWithAnnotation.getName()))
                .scan();
//
//        List<String> entityClassNames = Arrays.asList(
//                "stroom.security.Permission",
//                "stroom.security.AppPermission",
//                "stroom.security.DocumentPermission",
//                "stroom.security.User",
//                "stroom.security.UserGroupUser",
//
//                "stroom.streamtask.shared.StreamProcessor",
//                "stroom.streamtask.shared.StreamProcessorFilter",
//                "stroom.streamtask.shared.StreamProcessorFilterTracker",
//                "stroom.streamtask.shared.StreamTask",
//
//                "stroom.node.shared.Volume",
//                "stroom.node.shared.VolumeState",
//
//                "stroom.streamstore.shared.Stream",
//                "stroom.streamstore.shared.StreamVolume",
//                "stroom.streamstore.shared.StreamAttributeKey",
//                "stroom.streamstore.shared.StreamAttributeValue",
//                "stroom.feed.shared.Feed",
//                "stroom.streamstore.shared.StreamType",
//
//                "stroom.jobsystem.shared.Job",
//                "stroom.jobsystem.shared.JobNode",
//                "stroom.jobsystem.shared.ClusterLock",
//
//                "stroom.index.shared.Index",
//                "stroom.index.shared.IndexShard",
//
//                "stroom.statistics.shared.StatisticStoreEntity",
//                "stroom.stats.shared.StroomStatsStoreEntity",
//
//                "stroom.xmlschema.shared.XMLSchema",
//
//                "stroom.visualisation.shared.Visualisation",
//
//                "stroom.script.shared.Script",
//                "stroom.entity.shared.Res",
//
//                "stroom.pipeline.shared.PipelineEntity",
//                "stroom.pipeline.shared.XSLT",
//                stroom.pipeline.shared.TextConverter.class.getName(),
//
//                "stroom.dashboard.shared.Dashboard",
//
//                "stroom.dashboard.shared.QueryEntity",
//
////        addAnnotatedClass(stroom.stream.OldFolder)
//
//                "stroom.node.shared.Node",
//                "stroom.node.shared.Rack",
//
//                "stroom.node.shared.GlobalProperty",
//
//                "stroom.ruleset.shared.Policy",
//
//                "stroom.explorer.ExplorerTreeNode",
//                "stroom.explorer.ExplorerTreePath"
//        );


        PersistenceUnitInfoImpl persistenceUnitInfo = new PersistenceUnitInfoImpl(name, entityClassNames, properties(dataSource));

        String[] resources = resources();
        if (resources != null) {
            persistenceUnitInfo.getMappingFileNames().addAll(
                    Arrays.asList(resources)
            );
        }

        return persistenceUnitInfo;
    }

    private String[] resources() {
        return null;
    }

    private Properties properties(final DataSource dataSource) {
        Properties properties = new Properties();


        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLInnoDBDialect");
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.format_sql", "false");
        properties.put("hibernate.hbm2ddl.auto", "validate");

        if (dataSource != null) {
            properties.put("hibernate.connection.datasource", dataSource);
        }

        properties.put("hibernate.generate_statistics", Boolean.TRUE.toString());

        return properties;
    }

    private Integrator integrator() {
        return null;
    }

    private Context getContext() {
        final Deque<Context> deque = getDeque();
        return deque.peekLast();
    }

    private Deque<Context> getDeque() {
        Deque<Context> deque = threadLocal.get();
        if (deque == null) {
            deque = new ArrayDeque<>();
            threadLocal.set(deque);
        }
        return deque;
    }

    private interface Context {
        EntityManager getEntityManager();
    }

    private static class RootContext implements Context {
        private final EntityManager entityManager;

        RootContext(final EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @Override
        public EntityManager getEntityManager() {
            return entityManager;
        }
    }

    private static class ChildContext implements Context {
        private final EntityManager entityManager;

        ChildContext(final Context context) {
            this.entityManager = context.getEntityManager();
        }

        @Override
        public EntityManager getEntityManager() {
            return entityManager;
        }
    }


//    @Provides
//    public EntityManagerFactory entityManagerFactory(final ComboPooledDataSource dataSource, final Flyway flyway) {
//        final EntityManagerFactory emf = Persistence.createEntityManagerFactory("test");
//
//        final LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
//        entityManagerFactory.setDataSource(dataSource);
//        entityManagerFactory.setPersistenceUnitName("StroomPersistenceUnit");
//        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
//        entityManagerFactory.setPackagesToScan("stroom");
//
//        final Properties jpaProperties = new Properties();
//        jpaProperties.put("hibernate.hbm2ddl.auto", StroomProperties.getProperty("stroom.jpaHbm2DdlAuto"));
//        jpaProperties.put("hibernate.show_sql", StroomProperties.getProperty("stroom.showSql"));
//        jpaProperties.put("hibernate.dialect", StroomProperties.getProperty("stroom.jpaDialect"));
//        entityManagerFactory.setJpaProperties(jpaProperties);
//        return entityManagerFactory.getNativeEntityManagerFactory();
//
//    }

//    @Provides
//    @Singleton
//    ComboPooledDataSource dataSource(final GlobalProperties globalProperties, final StroomPropertyService stroomPropertyService) throws PropertyVetoException {
//        final ComboPooledDataSource dataSource = new ComboPooledDataSource();
//        dataSource.setDriverClass(StroomProperties.getProperty("stroom.jdbcDriverClassName"));
//        dataSource.setJdbcUrl(StroomProperties.getProperty("stroom.jdbcDriverUrl|trace"));
//        dataSource.setUser(StroomProperties.getProperty("stroom.jdbcDriverUsername"));
//        dataSource.setPassword(StroomProperties.getProperty("stroom.jdbcDriverPassword"));
//
//        final C3P0Config config = new C3P0Config("stroom.db.connectionPool.", stroomPropertyService);
//        dataSource.setMaxStatements(config.getMaxStatements());
//        dataSource.setMaxStatementsPerConnection(config.getMaxStatementsPerConnection());
//        dataSource.setInitialPoolSize(config.getInitialPoolSize());
//        dataSource.setMinPoolSize(config.getMinPoolSize());
//        dataSource.setMaxPoolSize(config.getMaxPoolSize());
//        dataSource.setIdleConnectionTestPeriod(config.getIdleConnectionTestPeriod());
//        dataSource.setMaxIdleTime(config.getMaxIdleTime());
//        dataSource.setAcquireIncrement(config.getAcquireIncrement());
//        dataSource.setAcquireRetryAttempts(config.getAcquireRetryAttempts());
//        dataSource.setAcquireRetryDelay(config.getAcquireRetryDelay());
//        dataSource.setCheckoutTimeout(config.getCheckoutTimeout());
//        dataSource.setMaxAdministrativeTaskTime(config.getMaxAdministrativeTaskTime());
//        dataSource.setMaxIdleTimeExcessConnections(config.getMaxIdleTimeExcessConnections());
//        dataSource.setMaxConnectionAge(config.getMaxConnectionAge());
//        dataSource.setUnreturnedConnectionTimeout(config.getUnreturnedConnectionTimeout());
//        dataSource.setStatementCacheNumDeferredCloseThreads(config.getStatementCacheNumDeferredCloseThreads());
//        dataSource.setNumHelperThreads(config.getNumHelperThreads());
//
//        dataSource.setPreferredTestQuery("select 1");
//        dataSource.setConnectionTesterClassName(StroomProperties.getProperty("stroom.connectionTesterClassName"));
//        return dataSource;
//    }
//
//    @Provides
//    @Singleton
//    Flyway flyway(final DataSource dataSource) {
//        final String jpaHbm2DdlAuto = StroomProperties.getProperty("stroom.jpaHbm2DdlAuto", "validate");
//        if (!"update".equals(jpaHbm2DdlAuto)) {
//            final Flyway flyway = new Flyway();
//            flyway.setDataSource(dataSource);
//
//            final String driver = StroomProperties.getProperty("stroom.jdbcDriverClassName");
//            if (driver.toLowerCase().contains("hsqldb")) {
//                flyway.setLocations("stroom/db/migration/hsqldb");
//            } else {
//                flyway.setLocations("stroom/db/migration/mysql");
//            }
//
//            Version version = null;
//            boolean usingFlyWay = false;
//            LOGGER.info("Testing installed Stroom schema version");
//
//            try (final Connection connection = dataSource.getConnection()) {
//                try {
//                    try (final Statement statement = connection.createStatement()) {
//                        try (final ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version ORDER BY installed_rank DESC")) {
//                            if (resultSet.next()) {
//                                usingFlyWay = true;
//
//                                final String ver = resultSet.getString(1);
//                                final String[] parts = ver.split("\\.");
//                                int maj = 0;
//                                int min = 0;
//                                int pat = 0;
//                                if (parts.length > 0) {
//                                    maj = Integer.valueOf(parts[0]);
//                                }
//                                if (parts.length > 1) {
//                                    min = Integer.valueOf(parts[1]);
//                                }
//                                if (parts.length > 2) {
//                                    pat = Integer.valueOf(parts[2]);
//                                }
//
//                                version = new Version(maj, min, pat);
//                                LOGGER.info("Found schema_version.version " + ver);
//                            }
//                        }
//                    }
//                } catch (final Exception e) {
//                    LOGGER.debug(e.getMessage());
//                    // Ignore.
//                }
//
//                if (version == null) {
//                    try {
//                        try (final Statement statement = connection.createStatement()) {
//                            try (final ResultSet resultSet = statement.executeQuery("SELECT VER_MAJ, VER_MIN, VER_PAT FROM STROOM_VER ORDER BY VER_MAJ DESC, VER_MIN DESC, VER_PAT DESC LIMIT 1")) {
//                                if (resultSet.next()) {
//                                    version = new Version(resultSet.getInt(1), resultSet.getInt(2), resultSet.getInt(3));
//                                    LOGGER.info("Found STROOM_VER.VER_MAJ/VER_MIN/VER_PAT " + version);
//                                }
//                            }
//                        }
//                    } catch (final Exception e) {
//                        LOGGER.debug(e.getMessage(), e);
//                        // Ignore.
//                    }
//                }
//
//                if (version == null) {
//                    try {
//                        try (final Statement statement = connection.createStatement()) {
//                            try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FD LIMIT 1")) {
//                                if (resultSet.next()) {
//                                    version = new Version(2, 0, 0);
//                                }
//                            }
//                        }
//                    } catch (final Exception e) {
//                        LOGGER.debug(e.getMessage(), e);
//                        // Ignore.
//                    }
//                }
//
//                if (version == null) {
//                    try {
//                        try (final Statement statement = connection.createStatement()) {
//                            try (final ResultSet resultSet = statement.executeQuery("SELECT ID FROM FEED LIMIT 1")) {
//                                if (resultSet.next()) {
//                                    version = new Version(2, 0, 0);
//                                }
//                            }
//                        }
//                    } catch (final Exception e) {
//                        LOGGER.debug(e.getMessage(), e);
//                        // Ignore.
//                    }
//                }
//            } catch (final SQLException e) {
//                LOGGER.error(MarkerFactory.getMarker("FATAL"), e.getMessage(), e);
//                throw new RuntimeException(e.getMessage(), e);
//            }
//
//            if (version != null) {
//                LOGGER.info("Detected current Stroom version is v" + version.toString());
//            } else {
//                LOGGER.info("This is a new installation!");
//            }
//
//            if (version == null) {
//                // If we have no version then this is a new Stroom instance so perform full FlyWay migration.
//                flyway.migrate();
//            } else if (usingFlyWay) {
//                // If we are already using FlyWay then allow FlyWay to attempt migration.
//                flyway.migrate();
//            } else if (version.getMajor() == 4 && version.getMinor() == 0 && version.getPatch() >= 60) {
//                // If Stroom is currently at v4.0.60+ then tell FlyWay to baseline at that version.
//                flyway.setBaselineVersionAsString("4.0.60");
//                flyway.baseline();
//                flyway.migrate();
//            } else {
//                final String message = "The current Stroom version cannot be upgraded to v5+. You must be on v4.0.60 or later.";
//                LOGGER.error(MarkerFactory.getMarker("FATAL"), message);
//                throw new RuntimeException(message);
//            }
//
//            return flyway;
//
//        }
//
//        return null;
//    }
//
//    @Provides
//    @Singleton
//    LocalContainerEntityManagerFactoryBean entityManagerFactory(
//            final ComboPooledDataSource dataSource, final Flyway flyway) {
//        final LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
//        entityManagerFactory.setDataSource(dataSource);
//        entityManagerFactory.setPersistenceUnitName("StroomPersistenceUnit");
//        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
//        entityManagerFactory.setPackagesToScan("stroom");
//
//        final Properties jpaProperties = new Properties();
//        jpaProperties.put("hibernate.hbm2ddl.auto", StroomProperties.getProperty("stroom.jpaHbm2DdlAuto"));
//        jpaProperties.put("hibernate.show_sql", StroomProperties.getProperty("stroom.showSql"));
//        jpaProperties.put("hibernate.dialect", StroomProperties.getProperty("stroom.jpaDialect"));
//        entityManagerFactory.setJpaProperties(jpaProperties);
//        return entityManagerFactory;
//    }
//
//    @Provides
//    @Singleton
//    PlatformTransactionManager transactionManager(final EntityManagerFactory entityManagerFactory) {
//        final JpaTransactionManager transactionManager = new JpaTransactionManager();
//        transactionManager.setEntityManagerFactory(entityManagerFactory);
//        return transactionManager;
//    }
}
