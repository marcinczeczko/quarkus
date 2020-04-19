package io.quarkus.amazon.common.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

/**
 * Amazon Services - Common configurations
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME, name = "amazon")
public class AmazonRuntimeConfig {

    /**
     * SDK client configurations for AWS Service clients
     */
    @ConfigItem(name = ConfigItem.PARENT)
    @ConfigDocMapKey("aws-client-name")
    public Map<String, SdkConfig> sdk;

    /**
     * Amazon Services - AWS service configurations
     */
    @ConfigItem(name = "aws")
    @ConfigDocMapKey("aws-client-name")
    @ConfigDocSection
    public Map<String, AwsConfig> aws;

    /**
     * Amazon Services - Sync HTTP transport configuration for Amazon clients
     */
    @ConfigItem(name = "sync-client")
    @ConfigDocMapKey("aws-client-name")
    @ConfigDocSection
    public Map<String, SyncHttpClientConfig> syncClient;

    /**
     * Amazon Services - Netty HTTP transport configuration for Amazon clients
     */
    @ConfigItem(name = "async-client")
    @ConfigDocMapKey("aws-client-name")
    @ConfigDocSection
    public Map<String, NettyHttpClientConfig> asyncClient;
}
