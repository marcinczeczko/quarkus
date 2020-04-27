package io.quarkus.dynamodb.runtime;

import io.quarkus.amazon.common.runtime.AwsConfig;
import io.quarkus.amazon.common.runtime.NettyHttpClientConfig;
import io.quarkus.amazon.common.runtime.SdkConfig;
import io.quarkus.amazon.common.runtime.SyncHttpClientConfig;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "dynamodb", phase = ConfigPhase.RUN_TIME)
public class DynamodbConfig {

    /**
     * Enable DynamoDB service endpoint discovery.
     */
    @ConfigItem
    public boolean enableEndpointDiscovery;

    /**
     * SDK client configurations for AWS dynamodb client
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public SdkConfig sdk;

    /**
     * AWS service configuration for DynamoDb client
     */
    @ConfigItem(name = ConfigItem.PARENT)
    @ConfigDocSection
    public AwsConfig aws;

    /**
     * Sync HTTP transport configuration for Amazon dynamodb client
     */
    @ConfigItem
    @ConfigDocSection
    public SyncHttpClientConfig syncClient;

    /**
     * Netty HTTP transport configuration for Amazon dynamodb client
     */
    @ConfigItem
    @ConfigDocSection
    public NettyHttpClientConfig asyncClient;
}
