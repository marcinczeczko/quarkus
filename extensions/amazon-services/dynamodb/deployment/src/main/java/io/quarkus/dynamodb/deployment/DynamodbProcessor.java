package io.quarkus.dynamodb.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.amazon.common.deployment.AbstractAmazonServiceProcessor;
import io.quarkus.amazon.common.deployment.AmazonClientBuildItem;
import io.quarkus.amazon.common.deployment.AmazonClientBuilderBuildItem;
import io.quarkus.amazon.common.deployment.AmazonClientBuilderConfiguredBuildItem;
import io.quarkus.amazon.common.deployment.AmazonClientInterceptorsPathBuildItem;
import io.quarkus.amazon.common.deployment.AmazonClientTransportsBuildItem;
import io.quarkus.amazon.common.runtime.AmazonClientRecorder;
import io.quarkus.amazon.common.runtime.AmazonClientTransportRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.dynamodb.runtime.DynamodbBuildTimeConfig;
import io.quarkus.dynamodb.runtime.DynamodbClientProducer;
import io.quarkus.dynamodb.runtime.DynamodbConfig;
import io.quarkus.dynamodb.runtime.DynamodbRecorder;
import io.quarkus.runtime.RuntimeValue;
import software.amazon.awssdk.http.SdkHttpClient.Builder;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamodbProcessor extends AbstractAmazonServiceProcessor {

    DynamodbBuildTimeConfig buildTimeConfig;

    @Override
    protected String amazonServiceClientName() {
        return FeatureBuildItem.DYNAMODB;
    }

    @Override
    protected DotName syncClientName() {
        return DotName.createSimple(DynamoDbClient.class.getName());
    }

    @Override
    protected DotName asyncClientName() {
        return DotName.createSimple(DynamoDbAsyncClient.class.getName());
    }

    @Override
    protected String builtinInterceptorsPath() {
        return "software/amazon/awssdk/services/dynamodb/execution.interceptors";
    }

    @BuildStep
    AdditionalBeanBuildItem producer() {
        return AdditionalBeanBuildItem.unremovableOf(DynamodbClientProducer.class);
    }

    @BuildStep
    void setup(BeanRegistrationPhaseBuildItem beanRegistrationPhase,
            BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<AmazonClientInterceptorsPathBuildItem> interceptors,
            BuildProducer<AmazonClientBuildItem> clientProducer) {

        setupExtension(beanRegistrationPhase, extensionSslNativeSupport, feature, interceptors, clientProducer,
                buildTimeConfig.sdk, buildTimeConfig.syncClient);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupTransport(DynamodbConfig runtimeConfig, DynamodbRecorder recorder, AmazonClientTransportRecorder transportRecorder,
            List<AmazonClientBuildItem> amazonClients,
            BuildProducer<AmazonClientTransportsBuildItem> clientTransportBuildProducer) {

        recorder.setTransportRecorder(transportRecorder);

        RuntimeValue<Builder> syncTransport = recorder
                .createSyncTransport(amazonServiceClientName(), buildTimeConfig, runtimeConfig);

        RuntimeValue<SdkAsyncHttpClient.Builder> asyncTransport = recorder
                .createAsyncTransport(amazonServiceClientName(), runtimeConfig);

        setupHttpClients(amazonClients, syncTransport, asyncTransport, clientTransportBuildProducer);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createClientBuilders(List<AmazonClientTransportsBuildItem> transportBuildItems, DynamodbRecorder recorder,
            DynamodbConfig runtimeConfig,
            BuildProducer<AmazonClientBuilderBuildItem> builderProducer) {

        createExtensionClients(transportBuildItems, builderProducer,
                (syncTransport) -> recorder.createSyncBuilder(runtimeConfig, syncTransport),
                (asyncTransport) -> recorder.createAsyncBuilder(runtimeConfig, asyncTransport));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureClient(List<AmazonClientBuilderBuildItem> clients, AmazonClientRecorder recorder,
            DynamodbConfig runtimeConfig,
            BuildProducer<AmazonClientBuilderConfiguredBuildItem> producer) {

        initClient(clients, recorder, runtimeConfig.aws, runtimeConfig.sdk, buildTimeConfig.sdk, producer);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void buildClients(List<AmazonClientBuilderConfiguredBuildItem> configuredClients, DynamodbRecorder recorder,
            BeanContainerBuildItem beanContainer,
            ShutdownContextBuildItem shutdown) {

        buildExtensionClients(configuredClients,
                (syncBuilder) -> recorder.buildClient(syncBuilder, beanContainer.getValue(), shutdown),
                (asyncBuilder) -> recorder.buildAsyncClient(asyncBuilder, beanContainer.getValue(), shutdown));
    }
}
