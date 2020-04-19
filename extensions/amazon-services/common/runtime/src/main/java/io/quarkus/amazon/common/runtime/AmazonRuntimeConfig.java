package io.quarkus.amazon.common.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.RUN_TIME, name = "amazon")
public class AmazonRuntimeConfig {

    /**
     * SDK client configurations for AWS Service clients
     */
    @ConfigItem(name = ConfigItem.PARENT)
    @ConfigDocMapKey("aws-client-name")
    public Map<String, SdkConfig> sdk;

    /**
     * AWS service configurations for AWS Service clients
     */
    @ConfigItem(name = "aws")
    @ConfigDocMapKey("aws-client-name")
    public Map<String, AwsConfig> aws;

    /**
     * Apache HTTP client transport configuration for AWS Service clients
     */
    @ConfigItem(name = "sync-client")
    @ConfigDocMapKey("aws-client-name")
    public Map<String, SyncHttpClientConfig> syncClient;

    /**
     * Netty HTTP client transport configuration for AWS Service clients
     */
    @ConfigItem(name = "async-client")
    @ConfigDocMapKey("aws-client-name")
    public Map<String, NettyHttpClientConfig> asyncClient;
}
