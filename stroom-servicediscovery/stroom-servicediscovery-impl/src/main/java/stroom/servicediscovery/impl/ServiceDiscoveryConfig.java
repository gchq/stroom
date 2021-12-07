package stroom.servicediscovery.impl;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ServiceDiscoveryConfig extends AbstractConfig {

    private final boolean enabled;
    private final String zookeeperUrl;
    private final String servicesHostNameOrIpAddress;
    private final int servicesPort;
    private final int curatorBaseSleepTimeMs;
    private final int curatorMaxSleepTimeMs;
    private final int curatorMaxRetries;
    private final String zookeeperBasePath;

    public ServiceDiscoveryConfig() {
        enabled = false;
        zookeeperUrl = "localhost:2181";
        servicesHostNameOrIpAddress = "localhost";
        servicesPort = 8080;
        curatorBaseSleepTimeMs = 5000;
        curatorMaxSleepTimeMs = 300000;
        curatorMaxRetries = 100;
        zookeeperBasePath = "/stroom-services";
    }

    @JsonCreator
    public ServiceDiscoveryConfig(
            @JsonProperty("enabled") final boolean enabled,
            @JsonProperty("zookeeperUrl") final String zookeeperUrl,
            @JsonProperty("servicesHostNameOrIpAddress") final String servicesHostNameOrIpAddress,
            @JsonProperty("servicesPort") final int servicesPort,
            @JsonProperty("curatorBaseSleepTimeMs") final int curatorBaseSleepTimeMs,
            @JsonProperty("curatorMaxSleepTimeMs") final int curatorMaxSleepTimeMs,
            @JsonProperty("curatorMaxRetries") final int curatorMaxRetries,
            @JsonProperty("zookeeperBasePath") final String zookeeperBasePath) {

        this.enabled = enabled;
        this.zookeeperUrl = zookeeperUrl;
        this.servicesHostNameOrIpAddress = servicesHostNameOrIpAddress;
        this.servicesPort = servicesPort;
        this.curatorBaseSleepTimeMs = curatorBaseSleepTimeMs;
        this.curatorMaxSleepTimeMs = curatorMaxSleepTimeMs;
        this.curatorMaxRetries = curatorMaxRetries;
        this.zookeeperBasePath = zookeeperBasePath;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Set this to true to use Zookeeper for service discovery. Set this to false to use " +
            "resolve all services locally, i.e. 127.0.0.1")
    public boolean isEnabled() {
        return enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The Zookeeper quorum connection string, required for service discovery, in the " +
            "form 'host1:port1,host2:port2,host3:port3'. The root znode to use in Zookeeper is defined in the " +
            "property stroom.serviceDiscovery.zookeeperBasePath")
    public String getZookeeperUrl() {
        return zookeeperUrl;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The external facing address that stroom will register its services with service " +
            "discovery. If this property is empty stroom will try to establish the hostname. Recommended to be left " +
            "blank in production to avoid having host specific configuration, unless the hostname is that of a load " +
            "balancer in front of stroom instances.")
    public String getServicesHostNameOrIpAddress() {
        return servicesHostNameOrIpAddress;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The external facing port that stroom will register its services with service discovery")
    public int getServicesPort() {
        return servicesPort;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Initial time in ms between retries to establish a connection to zookeeper")
    public int getCuratorBaseSleepTimeMs() {
        return curatorBaseSleepTimeMs;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Maximum time in ms between retries to establish a connection to zookeeper")
    public int getCuratorMaxSleepTimeMs() {
        return curatorMaxSleepTimeMs;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Maximum number of retries to establish a connection to zookeeper before giving up")
    public int getCuratorMaxRetries() {
        return curatorMaxRetries;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The base path to use in zookeeper for Curator service discover. All services " +
            "registering or querying discoverable services must use the same value for this base path. " +
            "Must start with a '/'")
    public String getZookeeperBasePath() {
        return zookeeperBasePath;
    }

    @Override
    public String toString() {
        return "ServiceDiscoveryConfig{" +
                "enabled=" + enabled +
                ", zookeeperUrl='" + zookeeperUrl + '\'' +
                ", servicesHostNameOrIpAddress='" + servicesHostNameOrIpAddress + '\'' +
                ", servicesPort=" + servicesPort +
                ", curatorBaseSleepTimeMs=" + curatorBaseSleepTimeMs +
                ", curatorMaxSleepTimeMs=" + curatorMaxSleepTimeMs +
                ", curatorMaxRetries=" + curatorMaxRetries +
                ", zookeeperBasePath='" + zookeeperBasePath + '\'' +
                '}';
    }
}
