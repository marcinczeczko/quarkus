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
@ConfigRoot(name = "amazon", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class AmazonBuildTimeConfig {

    /**
     * SDK client configurations for AWS Service clients
     */
    @ConfigItem(name = ConfigItem.PARENT)
    @ConfigDocMapKey("aws-client-name")
    public Map<String, SdkBuildTimeConfig> sdk;

    /**
     * Amazon Services - Sync HTTP transport configuration for Amazon clients
     */
    @ConfigItem(name = "sync-client")
    @ConfigDocMapKey("aws-client-name")
    @ConfigDocSection
    public Map<String, SyncHttpClientBuildTimeConfig> syncClient;
}
