package stroom.proxy.app.guice;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.ProxyConfigHolder;
import stroom.proxy.app.ProxyConfigMonitor;
import stroom.util.config.ConfigLocation;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.io.DirProvidersModule;
import stroom.util.validation.ValidationModule;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyConfigModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyConfigModule.class);

    private final ProxyConfigHolder proxyConfigHolder;

    public ProxyConfigModule(final ProxyConfigHolder proxyConfigHolder) {
        this.proxyConfigHolder = proxyConfigHolder;
    }

    @Override
    protected void configure() {
        // Bind the application config.
        bind(ProxyConfig.class).toInstance(proxyConfigHolder.getProxyConfig());

        bind(ProxyConfigMonitor.class).asEagerSingleton();

        install(new ProxyConfigProvidersModule());
        install(new DirProvidersModule());
        install(new ValidationModule());

        HasHealthCheckBinder.create(binder())
                .bind(ProxyConfigMonitor.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ProxyConfigMonitor.class);

        // Holder for the location of the yaml config file so the AppConfigMonitor can
        // get hold of it via guice
        bind(ConfigLocation.class)
                .toInstance(new ConfigLocation(proxyConfigHolder.getConfigFile()));

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own.
        // This allows gradle modules to know nothing about the other modules.
        // Our bind method has the arguments in the reverse way to guice so we can
        // more easily see the tree structure. Each config pojo must extend AbstractConfig.
        // Some config objects are shared and thus should not be bound here and should be
        // annotated with NotInjectableConfig

        // WARNING If you don't bind the class here and then inject that config class then
        // you will get a vanilla instance that is not linked to the config yml or db props!
        // It is safer to bind everything to be on the safe side
//        bindConfig(ProxyConfig::getContentSyncConfig, ProxyConfig::setContentSyncConfig, ContentSyncConfig.class);
//        bindConfig(ProxyConfig::getFeedStatusConfig, ProxyConfig::setFeedStatusConfig, FeedStatusConfig.class);
//        bindConfig(ProxyConfig::getFeedStatusConfig, ProxyConfig::setFeedStatusConfig, FeedStatusConfig.class);
//        bindConfig(ProxyConfig::getForwardStreamConfig, ProxyConfig::setForwardStreamConfig, ForwardStreamConfig.class);
//        bindConfig(ProxyConfig::getLogStreamConfig, ProxyConfig::setLogStreamConfig, LogStreamConfig.class);
//        // Bind it to PathConfig and ProxyPathConfig so classes that inject PathConfig work
//        bindConfig(ProxyConfig::getProxyPathConfig,
//                ProxyConfig::setProxyPathConfig,
//                ProxyPathConfig.class,
//                PathConfig.class);
//        bindConfig(ProxyConfig::getProxyRepositoryConfig,
//                ProxyConfig::setProxyRepositoryConfig,
//                ProxyRepositoryConfig.class);
//        bindConfig(ProxyConfig::getProxyRepositoryReaderConfig,
//                ProxyConfig::setProxyRepositoryReaderConfig,
//                ProxyRepositoryReaderConfig.class);
//        bindConfig(ProxyConfig::getProxyRequestConfig, ProxyConfig::setProxyRequestConfig, ProxyRequestConfig.class);
//        bindConfig(ProxyConfig::getRestClientConfig, ProxyConfig::setRestClientConfig, RestClientConfig.class);
    }

//    private <T extends IsProxyConfig> void bindConfig(
//            final Function<ProxyConfig, T> configGetter,
//            final BiConsumer<ProxyConfig, T> configSetter,
//            final Class<T> clazz) {
//        bindConfig(
//                proxyConfigHolder.getProxyConfig(),
//                configGetter,
//                configSetter,
//                clazz,
//                clazz,
//                null);
//    }
//
//    private <T extends IsProxyConfig> void bindConfig(
//            final Function<ProxyConfig, T> configGetter,
//            final BiConsumer<ProxyConfig, T> configSetter,
//            final Class<T> instanceClass,
//            final Class<? super T> bindClass) {
//        bindConfig(
//                proxyConfigHolder.getProxyConfig(),
//                configGetter,
//                configSetter,
//                instanceClass,
//                bindClass,
//                null);
//    }
//
//    private <T extends IsProxyConfig> void bindConfig(
//            final Function<ProxyConfig, T> configGetter,
//            final BiConsumer<ProxyConfig, T> configSetter,
//            final Class<T> clazz,
//            final Consumer<T> childConfigConsumer) {
//        bindConfig(
//                proxyConfigHolder.getProxyConfig(),
//                configGetter,
//                configSetter,
//                clazz,
//                clazz,
//                childConfigConsumer);
//    }
//
//    private <X extends IsProxyConfig, T extends IsProxyConfig> void bindConfig(
//            final X parentObject,
//            final Function<X, T> configGetter,
//            final BiConsumer<X, T> configSetter,
//            final Class<T> clazz) {
//        bindConfig(
//                parentObject,
//                configGetter,
//                configSetter,
//                clazz,
//                clazz,
//                null);
//    }
//
//    private <X extends IsProxyConfig, T extends IsProxyConfig> void bindConfig(
//            final X parentObject,
//            final Function<X, T> configGetter,
//            final BiConsumer<X, T> configSetter,
//            final Class<T> instanceClass,
//            final Class<? super T> bindClass,
//            final Consumer<T> childConfigConsumer) {
//
//        if (parentObject == null) {
//            throw new RuntimeException(LogUtil.message("Unable to bind config to {} as the parent is null. " +
//                            "You may have an empty branch in your config YAML file.",
//                    instanceClass.getCanonicalName()));
//        }
//
//        try {
//            // Get the config instance
//            T configInstance = configGetter.apply(parentObject);
//
//            if (configInstance == null) {
//                // branch with no children in the yaml so just create a default one
//                try {
//                    configInstance = instanceClass.getConstructor().newInstance();
//                    // Now set the new instance on the parent
//                    configSetter.accept(parentObject, configInstance);
//                } catch (Exception e) {
//                    throw new RuntimeException(LogUtil.message(
//                            "Class {} does not have a no args constructor", instanceClass.getName()));
//                }
//            }
//
//            LOGGER.debug("Binding instance of {} to class {}", instanceClass.getName(), bindClass.getName());
//            bind(bindClass).toInstance(configInstance);
//            if (!bindClass.equals(instanceClass)) {
//                // bind class and instance class differ so bind to both, e.g.
//                // an instance of ProxyPathConfig gets bound to PathConfig and ProxyPathConfig
//                LOGGER.debug("Binding instance of {} to class {}", instanceClass.getName(), instanceClass.getName());
//                bind(instanceClass).toInstance(configInstance);
//            }
//            if (childConfigConsumer != null) {
//                childConfigConsumer.accept(configInstance);
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(LogUtil.message("Error binding getter on object {} to class {}",
//                    parentObject.getClass().getCanonicalName(),
//                    bindClass.getCanonicalName()),
//                    e);
//        }
//    }
}
