package io.quarkus.amazon.common.deployment;

import java.util.Optional;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * Describes what clients are required for a given extension
 */
public final class AmazonClientBuildItem extends MultiBuildItem {
    private final Optional<DotName> syncClassName;
    private final Optional<DotName> asyncClassName;
    private final String extensionName;

    public AmazonClientBuildItem(Optional<DotName> syncClassName, Optional<DotName> asyncClassName,
            String extensionName) {
        this.syncClassName = syncClassName;
        this.asyncClassName = asyncClassName;
        this.extensionName = extensionName;
    }

    public Optional<DotName> getSyncClassName() {
        return syncClassName;
    }

    public Optional<DotName> getAsyncClassName() {
        return asyncClassName;
    }

    public String getExtensionName() {
        return extensionName;
    }
}