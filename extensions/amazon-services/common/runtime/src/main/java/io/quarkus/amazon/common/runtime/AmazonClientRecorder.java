package io.quarkus.amazon.common.runtime;

import java.net.URI;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.utils.StringUtils;

@Recorder
public class AmazonClientRecorder {
    private static final Log LOG = LogFactory.getLog(AmazonClientRecorder.class);

    private AmazonRuntimeConfig runtimeConfig;
    private AmazonBuildTimeConfig buildTimeConfig;

    public void configureRuntimeConfig(AmazonRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public void setBuildConfig(AmazonBuildTimeConfig buildConfig) {
        this.buildTimeConfig = buildConfig;
    }

    private Optional<SdkConfig> getSdkConfig(String awsServiceName) {
        return Optional.ofNullable(runtimeConfig.extensionSdk.get(awsServiceName));
    }

    private Optional<AwsConfig> getAwsConfig(String awsServiceName) {
        return Optional.ofNullable(runtimeConfig.extensionAws.get(awsServiceName));
    }

    private Optional<SdkBuildTimeConfig> getSdkBuildConfig(String awsServiceName) {
        return Optional.ofNullable(buildTimeConfig).map(cfg -> cfg.extensionSdk.get(awsServiceName));
    }

    public RuntimeValue<AwsClientBuilder> configureClient(RuntimeValue<? extends AwsClientBuilder> clientBuilder,
            String awsServiceName) {
        AwsClientBuilder builder = clientBuilder.getValue();

        initAwsClient(builder, awsServiceName, getAwsConfig(awsServiceName));
        initSdkClient(builder, awsServiceName, getSdkConfig(awsServiceName), getSdkBuildConfig(awsServiceName));

        return new RuntimeValue<>(builder);
    }

    public void initAwsClient(AwsClientBuilder builder, String extension, Optional<AwsConfig> config) {
        config.ifPresent(cfg -> {
            cfg.region.ifPresent(builder::region);

            if (cfg.credentials.type == AwsCredentialsProviderType.STATIC) {
                if (!cfg.credentials.staticProvider.accessKeyId.isPresent()
                        || !cfg.credentials.staticProvider.secretAccessKey.isPresent()) {
                    throw new RuntimeConfigurationError(
                            String.format("quarkus.%s.aws.credentials.static-provider.access-key-id and "
                                    + "quarkus.%s.aws.credentials.static-provider.secret-access-key cannot be empty if STATIC credentials provider used.",
                                    extension, extension));
                }
            }
            if (cfg.credentials.type == AwsCredentialsProviderType.PROCESS) {
                if (!cfg.credentials.processProvider.command.isPresent()) {
                    throw new RuntimeConfigurationError(
                            String.format(
                                    "quarkus.%s.aws.credentials.process-provider.command cannot be empty if PROCESS credentials provider used.",
                                    extension));
                }
            }

            builder.credentialsProvider(cfg.credentials.type.create(cfg.credentials));
        });
    }

    public void initSdkClient(SdkClientBuilder builder, String extension, Optional<SdkConfig> config,
            Optional<SdkBuildTimeConfig> buildConfig) {
        config.ifPresent(cfg -> {
            if (cfg.endpointOverride.isPresent()) {
                URI endpointOverride = cfg.endpointOverride.get();
                if (StringUtils.isBlank(endpointOverride.getScheme())) {
                    throw new RuntimeConfigurationError(
                            String.format("quarkus.%s.endpoint-override (%s) - scheme must be specified",
                                    extension,
                                    endpointOverride.toString()));
                }
            }

            cfg.endpointOverride.filter(URI::isAbsolute).ifPresent(builder::endpointOverride);

            final ClientOverrideConfiguration.Builder overrides = ClientOverrideConfiguration.builder();
            cfg.apiCallTimeout.ifPresent(overrides::apiCallTimeout);
            cfg.apiCallAttemptTimeout.ifPresent(overrides::apiCallAttemptTimeout);

            buildConfig.ifPresent(
                    buildCfg -> buildCfg.interceptors.orElse(Collections.emptyList()).stream()
                            .map(this::createInterceptor)
                            .filter(Objects::nonNull)
                            .forEach(overrides::addExecutionInterceptor));
            builder.overrideConfiguration(overrides.build());
        });
    }

    private ExecutionInterceptor createInterceptor(Class<?> interceptorClass) {
        try {
            return (ExecutionInterceptor) Class.forName(interceptorClass.getName()).newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            LOG.error("Unable to create interceptor", e);
            return null;
        }
    }
}
