package io.quarkus.amazon.common.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.RUN_TIME, name = "amazon")
public class AmazonRuntimeConfig {

    /**
     * SDK client configurations for AWS Service clients
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public Map<String, SdkConfig> extensionSdk;

    /**
     * AWS service configurations for AWS Service clients
     */
    @ConfigItem(name = "aws")
    public Map<String, AwsConfig> extensionAws;

    /**
     * Apache HTTP client transport configuration for AWS Service clients
     */
    @ConfigItem(name = "sync-client")
    public Map<String, SyncHttpClientConfig> extensionSyncClient;

    /**
     * Netty HTTP client transport configuration for AWS Service clients
     */
    @ConfigItem(name = "async-client")
    public Map<String, NettyHttpClientConfig> extensionAsyncClient;
}
