package io.quarkus.s3.runtime;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient.Builder;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Recorder
public class S3Recorder {
    public RuntimeValue<AwsClientBuilder> createSyncBuilder(S3Config config, RuntimeValue<Builder> transport) {
        S3ClientBuilder builder = S3Client.builder();
        configureS3Client(builder, config);

        if (transport != null) {
            builder.httpClientBuilder(transport.getValue());
        }
        return new RuntimeValue<>(builder);
    }

    public RuntimeValue<AwsClientBuilder> createAsyncBuilder(S3Config config,
            RuntimeValue<SdkAsyncHttpClient.Builder> transport) {

        S3AsyncClientBuilder builder = S3AsyncClient.builder();
        configureS3Client(builder, config);

        if (transport != null) {
            builder.httpClientBuilder(transport.getValue());
        }
        return new RuntimeValue<>(builder);
    }

    public RuntimeValue<S3Client> buildClient(RuntimeValue<? extends AwsClientBuilder> builder,
            BeanContainer beanContainer,
            ShutdownContext shutdown) {
        S3ClientProducer producer = beanContainer.instance(S3ClientProducer.class);
        producer.setSyncConfiguredBuilder((S3ClientBuilder) builder.getValue());
        shutdown.addShutdownTask(producer::destroy);
        return new RuntimeValue<>(producer.client());
    }

    public RuntimeValue<S3AsyncClient> buildAsyncClient(RuntimeValue<? extends AwsClientBuilder> builder,
            BeanContainer beanContainer,
            ShutdownContext shutdown) {
        S3ClientProducer producer = beanContainer.instance(S3ClientProducer.class);
        producer.setAsyncConfiguredBuilder((S3AsyncClientBuilder) builder.getValue());
        shutdown.addShutdownTask(producer::destroy);
        return new RuntimeValue<>(producer.asyncClient());
    }

    private void configureS3Client(S3BaseClientBuilder builder, S3Config config) {
        builder.serviceConfiguration(
                S3Configuration.builder()
                        .accelerateModeEnabled(config.accelerateMode)
                        .checksumValidationEnabled(config.checksumValidation)
                        .chunkedEncodingEnabled(config.chunkedEncoding)
                        .dualstackEnabled(config.dualstack)
                        .pathStyleAccessEnabled(config.pathStyleAccess).build());
    }
}
