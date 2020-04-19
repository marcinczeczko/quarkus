package io.quarkus.amazon.common.deployment;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.processor.BuildExtension;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.runtime.RuntimeValue;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

abstract public class AbstractAmazonServiceProcessor {

    abstract protected String amazonServiceClientName();

    abstract protected DotName syncClientName();

    abstract protected DotName asyncClientName();

    abstract protected String builtinInterceptorsPath();

    protected void setupExtension(BeanRegistrationPhaseBuildItem beanRegistrationPhase,
            BuildProducer<ExtensionSslNativeSupportBuildItem> extensionSslNativeSupport,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<AmazonClientInterceptorsPathBuildItem> interceptors,
            BuildProducer<AmazonClientBuildItem> clientProducer) {

        feature.produce(new FeatureBuildItem(amazonServiceClientName()));
        extensionSslNativeSupport.produce(new ExtensionSslNativeSupportBuildItem(amazonServiceClientName()));
        interceptors.produce(new AmazonClientInterceptorsPathBuildItem(builtinInterceptorsPath()));

        Optional<DotName> syncClassName = Optional.empty();
        Optional<DotName> asyncClassName = Optional.empty();

        //Discover all clients injections in order to determine if async or sync client is required
        for (InjectionPointInfo injectionPoint : beanRegistrationPhase.getContext().get(BuildExtension.Key.INJECTION_POINTS)) {
            Type requiredType = injectionPoint.getRequiredType();

            if (syncClientName().equals(requiredType.name())) {
                syncClassName = Optional.of(syncClientName());
            }
            if (asyncClientName().equals(requiredType.name())) {
                asyncClassName = Optional.of(asyncClientName());
            }
        }
        if (syncClassName.isPresent() || asyncClassName.isPresent()) {
            clientProducer.produce(new AmazonClientBuildItem(syncClassName, asyncClassName, amazonServiceClientName()));
        }
    }

    protected void createExtensionClients(List<AmazonClientTransportsBuildItem> clients,
            BuildProducer<AmazonClientBuilderBuildItem> builderProducer,
            Function<RuntimeValue<SdkHttpClient.Builder>, RuntimeValue<AwsClientBuilder>> syncFunc,
            Function<RuntimeValue<SdkAsyncHttpClient.Builder>, RuntimeValue<AwsClientBuilder>> asyncFunc) {

        for (AmazonClientTransportsBuildItem client : clients) {
            if (amazonServiceClientName().equals(client.getAwsClientName())) {
                RuntimeValue<AwsClientBuilder> syncBuilder = null;
                RuntimeValue<AwsClientBuilder> asyncBuilder = null;
                if (client.getSyncClassName().isPresent()) {
                    syncBuilder = syncFunc.apply(client.getSyncTransport());
                }
                if (client.getAsyncClassName().isPresent()) {
                    asyncBuilder = asyncFunc.apply(client.getAsyncTransport());
                }
                builderProducer.produce(new AmazonClientBuilderBuildItem(client.getAwsClientName(), syncBuilder, asyncBuilder));
            }
        }
    }

    protected void buildExtensionClients(List<AmazonClientBuilderConfiguredBuildItem> configuredClients,
            Function<RuntimeValue<? extends AwsClientBuilder>, RuntimeValue<? extends SdkClient>> syncClient,
            Function<RuntimeValue<? extends AwsClientBuilder>, RuntimeValue<? extends SdkClient>> asyncClient) {

        for (AmazonClientBuilderConfiguredBuildItem client : configuredClients) {
            if (amazonServiceClientName().equals(client.getAwsClientName())) {
                if (client.getSyncBuilder() != null) {
                    syncClient.apply(client.getSyncBuilder());
                }
                if (client.getAsyncBuilder() != null) {
                    asyncClient.apply(client.getAsyncBuilder());
                }
            }
        }
    }
}
