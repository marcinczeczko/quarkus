package io.quarkus.amazon.common.deployment;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.DeploymentException;

import org.jboss.jandex.DotName;

import io.quarkus.amazon.common.runtime.AmazonBuildTimeConfig;
import io.quarkus.amazon.common.runtime.AmazonClientRecorder;
import io.quarkus.amazon.common.runtime.AmazonClientTransportRecorder;
import io.quarkus.amazon.common.runtime.AmazonRuntimeConfig;
import io.quarkus.amazon.common.runtime.SdkBuildTimeConfig;
import io.quarkus.amazon.common.runtime.SyncHttpClientBuildTimeConfig;
import io.quarkus.amazon.common.runtime.SyncHttpClientBuildTimeConfig.SyncClientType;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.JniBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.configuration.ConfigurationError;
import io.quarkus.runtime.RuntimeValue;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpClient.Builder;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpService;

public class AmazonServicesClientsProcessor {
    public static final String AWS_SDK_APPLICATION_ARCHIVE_MARKERS = "software/amazon/awssdk";

    private static final String APACHE_HTTP_SERVICE = "software.amazon.awssdk.http.apache.ApacheSdkHttpService";
    private static final String NETTY_HTTP_SERVICE = "software.amazon.awssdk.http.nio.netty.NettySdkAsyncHttpService";
    private static final String URL_HTTP_SERVICE = "software.amazon.awssdk.http.urlconnection.UrlConnectionSdkHttpService";

    private static final DotName EXECUTION_INTERCEPTOR_NAME = DotName.createSimple(ExecutionInterceptor.class.getName());

    AmazonBuildTimeConfig buildTimeConfig;

    @BuildStep
    JniBuildItem jni() {
        return new JniBuildItem();
    }

    @BuildStep
    void globalInterceptors(BuildProducer<AmazonClientInterceptorsPathBuildItem> producer) {
        producer.produce(
                new AmazonClientInterceptorsPathBuildItem("software/amazon/awssdk/global/handlers/execution.interceptors"));
    }

    @BuildStep
    AdditionalApplicationArchiveMarkerBuildItem awsAppArchiveMarkers() {
        return new AdditionalApplicationArchiveMarkerBuildItem(AWS_SDK_APPLICATION_ARCHIVE_MARKERS);
    }

    @BuildStep(loadsApplicationClasses = true)
    void setup(CombinedIndexBuildItem combinedIndexBuildItem,
            List<AmazonClientBuildItem> amazonClients,
            List<AmazonClientInterceptorsPathBuildItem> interceptors,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<NativeImageResourceBuildItem> resource,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyDefinition,
            BuildProducer<ServiceProviderBuildItem> serviceProvider) {

        interceptors.stream().map(AmazonClientInterceptorsPathBuildItem::getInterceptorsPath)
                .forEach(path -> resource.produce(new NativeImageResourceBuildItem(path)));

        //Discover all interceptor implementations
        List<String> knownInterceptorImpls = combinedIndexBuildItem.getIndex()
                .getAllKnownImplementors(EXECUTION_INTERCEPTOR_NAME)
                .stream()
                .map(c -> c.name().toString()).collect(Collectors.toList());

        //Collect all Amazon Services extensions used
        List<String> amazonExtensions = amazonClients.stream().map(AmazonClientBuildItem::getExtensionName)
                .collect(Collectors.toList());

        //Validate configurations
        checkNamedConfigs(amazonExtensions);

        for (String extension : amazonExtensions) {
            SdkBuildTimeConfig extensionSdk = buildTimeConfig.sdk.get(extension);
            if (extensionSdk != null) {
                extensionSdk.interceptors.orElse(Collections.emptyList()).forEach(interceptorClass -> {
                    if (!knownInterceptorImpls.contains(interceptorClass.getName())) {
                        throw new ConfigurationError(
                                String.format(
                                        "quarkus.dynamodb.sdk.interceptors (%s) - must list only existing implementations of software.amazon.awssdk.core.interceptor.ExecutionInterceptor",
                                        extensionSdk.interceptors.toString()));
                    }
                });
            }
        }

        reflectiveClasses.produce(new ReflectiveClassBuildItem(false, false,
                knownInterceptorImpls.toArray(new String[knownInterceptorImpls.size()])));

        reflectiveClasses
                .produce(new ReflectiveClassBuildItem(true, false, "com.sun.xml.internal.stream.XMLInputFactoryImpl"));
        reflectiveClasses
                .produce(new ReflectiveClassBuildItem(true, false, "com.sun.xml.internal.stream.XMLOutputFactoryImpl"));

        boolean syncTransportNeeded = amazonClients.stream().anyMatch(item -> item.getSyncClassName().isPresent());
        boolean asyncTransportNeeded = amazonClients.stream().anyMatch(item -> item.getAsyncClassName().isPresent());

        //Register only clients that are used
        if (syncTransportNeeded) {
            if (findRequiredSyncTypes(amazonExtensions).contains(SyncClientType.APACHE)) {
                checkClasspath(APACHE_HTTP_SERVICE, "apache-client");
                //Register Apache client as sync client
                proxyDefinition
                        .produce(new NativeImageProxyDefinitionBuildItem("org.apache.http.conn.HttpClientConnectionManager",
                                "org.apache.http.pool.ConnPoolControl",
                                "software.amazon.awssdk.http.apache.internal.conn.Wrapped"));

                serviceProvider.produce(
                        new ServiceProviderBuildItem(SdkHttpService.class.getName(), APACHE_HTTP_SERVICE));
            } else {
                checkClasspath(URL_HTTP_SERVICE, "url-connection-client");
                serviceProvider.produce(new ServiceProviderBuildItem(SdkHttpService.class.getName(), URL_HTTP_SERVICE));
            }
        }

        if (asyncTransportNeeded) {
            checkClasspath(NETTY_HTTP_SERVICE, "netty-nio-client");
            //Register netty as async client
            serviceProvider.produce(
                    new ServiceProviderBuildItem(SdkAsyncHttpService.class.getName(),
                            NETTY_HTTP_SERVICE));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createTransportBuilders(AmazonClientTransportRecorder transportRecorder,
            AmazonRuntimeConfig runtimeConfig,
            List<AmazonClientBuildItem> amazonClients,
            BuildProducer<AmazonClientTransportsBuildItem> clientTransports) {

        if (!amazonClients.isEmpty()) {
            transportRecorder.configureRuntimeConfig(runtimeConfig);
            transportRecorder.setBuildConfig(buildTimeConfig);
        }

        for (AmazonClientBuildItem client : amazonClients) {
            RuntimeValue<Builder> syncTransport = null;
            RuntimeValue<SdkAsyncHttpClient.Builder> asyncTransport = null;

            if (client.getSyncClassName().isPresent()) {
                syncTransport = transportRecorder.createSyncTransport(client.getExtensionName());
            }

            if (client.getAsyncClassName().isPresent()) {
                asyncTransport = transportRecorder.createAsyncTransport(client.getExtensionName());
            }

            clientTransports.produce(
                    new AmazonClientTransportsBuildItem(
                            client.getSyncClassName(), client.getAsyncClassName(),
                            syncTransport,
                            asyncTransport,
                            client.getExtensionName()));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureClients(List<AmazonClientBuilderBuildItem> clients, AmazonClientRecorder recorder,
            AmazonRuntimeConfig runtimeConfig,
            BuildProducer<AmazonClientBuilderConfiguredBuildItem> producer) {

        if (!clients.isEmpty()) {
            recorder.configureRuntimeConfig(runtimeConfig);
            recorder.setBuildConfig(buildTimeConfig);
        }

        for (AmazonClientBuilderBuildItem client : clients) {
            RuntimeValue<? extends AwsClientBuilder> syncBuilder = null;
            RuntimeValue<? extends AwsClientBuilder> asyncBuilder = null;

            if (client.getSyncBuilder() != null) {
                syncBuilder = recorder.configureClient(client.getSyncBuilder(), client.getExtensionName());
            }
            if (client.getAsyncBuilder() != null) {
                asyncBuilder = recorder.configureClient(client.getAsyncBuilder(), client.getExtensionName());
            }
            producer.produce(new AmazonClientBuilderConfiguredBuildItem(client.getExtensionName(), syncBuilder, asyncBuilder));
        }
    }

    private Set<SyncClientType> findRequiredSyncTypes(List<String> amazonExtensions) {
        return amazonExtensions.stream().map(ext -> {
            SyncHttpClientBuildTimeConfig extSyncConfig = buildTimeConfig.syncClient.get(ext);
            if (extSyncConfig != null) {
                return extSyncConfig.type;
            } else { //If no syncConfig for extension available, URL connection client is used by default
                return SyncClientType.URL;
            }
        }).collect(Collectors.toSet());
    }

    private void checkClasspath(String className, String dependencyName) {
        try {
            Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new DeploymentException(
                    "Missing 'software.amazon.awssdk:" + dependencyName + "' dependency on the classpath");
        }
    }

    private void checkNamedConfigs(List<String> amazonExtensions) {
        for (String serviceName : buildTimeConfig.sdk.keySet()) {
            if (!amazonExtensions.contains(serviceName)) {
                throw new ConfigurationError(
                        String.format("quarkus.%s - Given Amazon Service client extension is not valid or not used.",
                                serviceName));
            }
        }
        for (String serviceName : buildTimeConfig.syncClient.keySet()) {
            if (!amazonExtensions.contains(serviceName)) {
                throw new ConfigurationError(
                        String.format(
                                "quarkus.%s.sync-client - Given Amazon Service client extension is not valid or not used.",
                                serviceName));
            }
        }
    }
}
