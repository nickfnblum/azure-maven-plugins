package com.microsoft.azure.toolkit.lib.containerregistry.config;

import lombok.Data;

@Data
public class ContainerRegistryConfig {
    private String subscriptionId;
    private String resourceGroup;
    private String registryName;
    private String region;
    private String sku;
}
