package io.quarkus.amazon.common.deployment;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.runtime.RuntimeValue;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;

public final class AmazonClientBuilderConfiguredBuildItem extends MultiBuildItem {
    private final String extensionName;
    private final RuntimeValue<? extends AwsClientBuilder> syncBuilder;
    private final RuntimeValue<? extends AwsClientBuilder> asyncBuilder;

    public AmazonClientBuilderConfiguredBuildItem(String extensionName, RuntimeValue<? extends AwsClientBuilder> syncBuilder,
            RuntimeValue<? extends AwsClientBuilder> asyncBuilder) {
        this.extensionName = extensionName;
        this.syncBuilder = syncBuilder;
        this.asyncBuilder = asyncBuilder;
    }

    public String getExtensionName() {
        return extensionName;
    }

    public RuntimeValue<? extends AwsClientBuilder> getSyncBuilder() {
        return syncBuilder;
    }

    public RuntimeValue<? extends AwsClientBuilder> getAsyncBuilder() {
        return asyncBuilder;
    }
}
