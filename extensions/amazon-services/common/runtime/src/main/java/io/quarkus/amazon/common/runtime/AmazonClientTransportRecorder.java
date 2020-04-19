package io.quarkus.amazon.common.runtime;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import io.quarkus.amazon.common.runtime.SyncHttpClientBuildTimeConfig.SyncClientType;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient.Builder;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.Http2Configuration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.utils.StringUtils;
import software.amazon.awssdk.utils.ThreadFactoryBuilder;

@Recorder
public class AmazonClientTransportRecorder {
    private AmazonRuntimeConfig config;
    private AmazonBuildTimeConfig buildTimeConfig;

    public void configureRuntimeConfig(AmazonRuntimeConfig config) {
        this.config = config;
    }

    public void setBuildConfig(AmazonBuildTimeConfig buildConfig) {
        this.buildTimeConfig = buildConfig;
    }

    private Optional<SyncHttpClientConfig> getSyncRuntimeConfig(String awsServiceName) {
        return Optional.ofNullable(config.syncClient.get(awsServiceName));
    }

    private Optional<NettyHttpClientConfig> getAsyncRuntimeConfig(String awsServiceName) {
        return Optional.ofNullable(config.asyncClient.get(awsServiceName));
    }

    private SyncClientType getExtensionSyncClientType(String extension) {
        return Optional.ofNullable(buildTimeConfig.syncClient.get(extension))
                .map(cfg -> cfg.type)
                .orElse(SyncClientType.URL);
    }

    public RuntimeValue<SdkHttpClient.Builder> createSyncTransport(String awsServiceName) {
        SdkHttpClient.Builder syncBuilder;

        Optional<SyncHttpClientConfig> config = getSyncRuntimeConfig(awsServiceName);

        if (getExtensionSyncClientType(awsServiceName) == SyncClientType.APACHE) {
            Builder builder = ApacheHttpClient.builder();

            config.ifPresent(cfg -> {
                validateApacheClientConfig(awsServiceName, cfg);

                builder.connectionTimeout(cfg.connectionTimeout);
                builder.connectionAcquisitionTimeout(cfg.apache.connectionAcquisitionTimeout);
                builder.connectionMaxIdleTime(cfg.apache.connectionMaxIdleTime);
                cfg.apache.connectionTimeToLive.ifPresent(builder::connectionTimeToLive);
                builder.expectContinueEnabled(cfg.apache.expectContinueEnabled);
                builder.maxConnections(cfg.apache.maxConnections);
                builder.socketTimeout(cfg.socketTimeout);
                builder.useIdleConnectionReaper(cfg.apache.useIdleConnectionReaper);

                if (cfg.apache.proxy.enabled && cfg.apache.proxy.endpoint.isPresent()) {
                    ProxyConfiguration.Builder proxyBuilder = ProxyConfiguration.builder()
                            .endpoint(cfg.apache.proxy.endpoint.get());
                    cfg.apache.proxy.username.ifPresent(proxyBuilder::username);
                    cfg.apache.proxy.password.ifPresent(proxyBuilder::password);
                    cfg.apache.proxy.nonProxyHosts.ifPresent(c -> c.forEach(proxyBuilder::addNonProxyHost));
                    cfg.apache.proxy.ntlmDomain.ifPresent(proxyBuilder::ntlmDomain);
                    cfg.apache.proxy.ntlmWorkstation.ifPresent(proxyBuilder::ntlmWorkstation);
                    cfg.apache.proxy.preemptiveBasicAuthenticationEnabled
                            .ifPresent(proxyBuilder::preemptiveBasicAuthenticationEnabled);

                    builder.proxyConfiguration(proxyBuilder.build());
                }

                builder.tlsKeyManagersProvider(cfg.apache.tlsManagersProvider.type.create(cfg.apache.tlsManagersProvider));
            });

            syncBuilder = builder;
        } else {
            UrlConnectionHttpClient.Builder builder = UrlConnectionHttpClient.builder();
            config.ifPresent(cfg -> {
                builder.connectionTimeout(cfg.connectionTimeout);
                builder.socketTimeout(cfg.socketTimeout);
            });

            syncBuilder = builder;
        }

        return new RuntimeValue<>(syncBuilder);
    }

    public RuntimeValue<SdkAsyncHttpClient.Builder> createAsyncTransport(String awsServiceName) {
        NettyNioAsyncHttpClient.Builder builder = NettyNioAsyncHttpClient.builder();

        getAsyncRuntimeConfig(awsServiceName).ifPresent(cfg -> {
            validateNettyClientConfig(awsServiceName, cfg);

            builder.connectionAcquisitionTimeout(cfg.connectionAcquisitionTimeout);
            builder.connectionMaxIdleTime(cfg.connectionMaxIdleTime);
            builder.connectionTimeout(cfg.connectionTimeout);
            cfg.connectionTimeToLive.ifPresent(builder::connectionTimeToLive);
            builder.maxConcurrency(cfg.maxConcurrency);
            builder.maxPendingConnectionAcquires(cfg.maxPendingConnectionAcquires);
            builder.protocol(cfg.protocol);
            builder.readTimeout(cfg.readTimeout);
            builder.writeTimeout(cfg.writeTimeout);
            cfg.sslProvider.ifPresent(builder::sslProvider);
            builder.useIdleConnectionReaper(cfg.useIdleConnectionReaper);

            if (cfg.http2.initialWindowSize.isPresent() || cfg.http2.maxStreams.isPresent()) {
                Http2Configuration.Builder http2Builder = Http2Configuration.builder();
                cfg.http2.initialWindowSize.ifPresent(http2Builder::initialWindowSize);
                cfg.http2.maxStreams.ifPresent(http2Builder::maxStreams);
                builder.http2Configuration(http2Builder.build());
            }

            if (cfg.proxy.enabled && cfg.proxy.endpoint.isPresent()) {
                software.amazon.awssdk.http.nio.netty.ProxyConfiguration.Builder proxyBuilder = software.amazon.awssdk.http.nio.netty.ProxyConfiguration
                        .builder().scheme(cfg.proxy.endpoint.get().getScheme())
                        .host(cfg.proxy.endpoint.get().getHost())
                        .nonProxyHosts(new HashSet<>(cfg.proxy.nonProxyHosts.orElse(Collections.emptyList())));

                if (cfg.proxy.endpoint.get().getPort() != -1) {
                    proxyBuilder.port(cfg.proxy.endpoint.get().getPort());
                }
                builder.proxyConfiguration(proxyBuilder.build());
            }

            builder.tlsKeyManagersProvider(cfg.tlsManagersProvider.type.create(cfg.tlsManagersProvider));

            if (cfg.eventLoop.override) {
                SdkEventLoopGroup.Builder eventLoopBuilder = SdkEventLoopGroup.builder();
                cfg.eventLoop.numberOfThreads.ifPresent(eventLoopBuilder::numberOfThreads);
                if (cfg.eventLoop.threadNamePrefix.isPresent()) {
                    eventLoopBuilder.threadFactory(
                            new ThreadFactoryBuilder().threadNamePrefix(cfg.eventLoop.threadNamePrefix.get()).build());
                }
                builder.eventLoopGroupBuilder(eventLoopBuilder);
            }
        });

        return new RuntimeValue<>(builder);
    }

    private void validateApacheClientConfig(String extension, SyncHttpClientConfig config) {
        if (config.apache.maxConnections <= 0) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.sync-client.max-connections may not be negative or zero.", extension));
        }
        if (config.apache.proxy.enabled) {
            config.apache.proxy.endpoint.ifPresent(uri -> validateProxyEndpoint(extension, uri, "sync"));
        }
        validateTlsManagersProvider(extension, config.apache.tlsManagersProvider, "sync");
    }

    private void validateNettyClientConfig(String extension, NettyHttpClientConfig config) {
        if (config.maxConcurrency <= 0) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.async-client.max-concurrency may not be negative or zero.", extension));
        }

        if (config.http2.maxStreams.isPresent() && config.http2.maxStreams.get() <= 0) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.async-client.http2.max-streams may not be negative.", extension));
        }

        if (config.http2.initialWindowSize.isPresent() && config.http2.initialWindowSize.get() <= 0) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.async-client.http2.initial-window-size may not be negative.", extension));
        }

        if (config.maxPendingConnectionAcquires <= 0) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.async-client.max-pending-connection-acquires may not be negative or zero.",
                            extension));
        }
        if (config.eventLoop.override) {
            if (config.eventLoop.numberOfThreads.isPresent() && config.eventLoop.numberOfThreads.get() <= 0) {
                throw new RuntimeConfigurationError(
                        String.format("quarkus.%s.async-client.event-loop.number-of-threads may not be negative or zero.",
                                extension));
            }
        }
        if (config.proxy.enabled) {
            config.proxy.endpoint.ifPresent(uri -> validateProxyEndpoint(extension, uri, "async"));
        }

        validateTlsManagersProvider(extension, config.tlsManagersProvider, "async");
    }

    private void validateProxyEndpoint(String extension, URI endpoint, String clientType) {
        if (StringUtils.isBlank(endpoint.getScheme())) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.%s-client.proxy.endpoint (%s) - scheme must be specified",
                            extension, clientType, endpoint.toString()));
        }
        if (StringUtils.isBlank(endpoint.getHost())) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.%s-client.proxy.endpoint (%s) - host must be specified",
                            extension, clientType, endpoint.toString()));
        }
        if (StringUtils.isNotBlank(endpoint.getUserInfo())) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.%s-client.proxy.endpoint (%s) - user info is not supported.",
                            extension, clientType, endpoint.toString()));
        }
        if (StringUtils.isNotBlank(endpoint.getPath())) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.%s-client.proxy.endpoint (%s) - path is not supported.",
                            extension, clientType, endpoint.toString()));
        }
        if (StringUtils.isNotBlank(endpoint.getQuery())) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.%s-client.proxy.endpoint (%s) - query is not supported.",
                            extension, clientType, endpoint.toString()));
        }
        if (StringUtils.isNotBlank(endpoint.getFragment())) {
            throw new RuntimeConfigurationError(
                    String.format("quarkus.%s.%s-client.proxy.endpoint (%s) - fragment is not supported.",
                            extension, clientType, endpoint.toString()));
        }
    }

    private void validateTlsManagersProvider(String extension, TlsManagersProviderConfig config, String clientType) {
        if (config != null && config.type == TlsManagersProviderType.FILE_STORE) {
            if (config.fileStore == null) {
                throw new RuntimeConfigurationError(
                        String.format(
                                "quarkus.%s.%s-client.tls-managers-provider.file-store must be specified if 'FILE_STORE' provider type is used",
                                extension, clientType));
            } else {
                if (!config.fileStore.password.isPresent()) {
                    throw new RuntimeConfigurationError(
                            String.format(
                                    "quarkus.%s.%s-client.tls-managers-provider.file-store.path should not be empty if 'FILE_STORE' provider is used.",
                                    extension, clientType));
                }
                if (!config.fileStore.type.isPresent()) {
                    throw new RuntimeConfigurationError(
                            String.format(
                                    "quarkus.%s.%s-client.tls-managers-provider.file-store.type should not be empty if 'FILE_STORE' provider is used.",
                                    extension, clientType));
                }
                if (!config.fileStore.password.isPresent()) {
                    throw new RuntimeConfigurationError(
                            String.format(
                                    "quarkus.%s.%s-client.tls-managers-provider.file-store.password should not be empty if 'FILE_STORE' provider is used.",
                                    extension, clientType));
                }
            }
        }
    }
}
