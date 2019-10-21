package io.quarkus.dynamodb.runtime;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient.Builder;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

@Recorder
public class DynamodbRecorder {
    public RuntimeValue<AwsClientBuilder> createSyncBuilder(DynamodbConfig config, RuntimeValue<Builder> transport) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder();
        if (config.enableEndpointDiscovery) {
            builder.enableEndpointDiscovery();
        }
        if (transport != null) {
            builder.httpClientBuilder(transport.getValue());
        }
        return new RuntimeValue<>(builder);
    }

    public RuntimeValue<AwsClientBuilder> createAsyncBuilder(DynamodbConfig config,
            RuntimeValue<SdkAsyncHttpClient.Builder> transport) {

        DynamoDbAsyncClientBuilder builder = DynamoDbAsyncClient.builder();
        if (config.enableEndpointDiscovery) {
            builder.enableEndpointDiscovery();
        }
        if (transport != null) {
            builder.httpClientBuilder(transport.getValue());
        }
        return new RuntimeValue<>(builder);
    }

    public RuntimeValue<DynamoDbClient> buildClient(RuntimeValue<? extends AwsClientBuilder> builder,
            BeanContainer beanContainer,
            ShutdownContext shutdown) {
        DynamodbClientProducer producer = beanContainer.instance(DynamodbClientProducer.class);
        producer.setSyncConfiguredBuilder((DynamoDbClientBuilder) builder.getValue());
        shutdown.addShutdownTask(producer::destroy);
        return new RuntimeValue<>(producer.client());
    }

    public RuntimeValue<DynamoDbAsyncClient> buildAsyncClient(RuntimeValue<? extends AwsClientBuilder> builder,
            BeanContainer beanContainer,
            ShutdownContext shutdown) {
        DynamodbClientProducer producer = beanContainer.instance(DynamodbClientProducer.class);
        producer.setAsyncConfiguredBuilder((DynamoDbAsyncClientBuilder) builder.getValue());
        shutdown.addShutdownTask(producer::destroy);
        return new RuntimeValue<>(producer.asyncClient());
    }

}
