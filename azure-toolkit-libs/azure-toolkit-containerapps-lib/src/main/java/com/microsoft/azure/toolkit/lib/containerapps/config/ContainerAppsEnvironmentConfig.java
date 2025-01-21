package com.microsoft.azure.toolkit.lib.containerapps.config;

import lombok.Data;

@Data
public class ContainerAppsEnvironmentConfig {
    private String subscriptionId;
    private String resourceGroup;
    private String appEnvironmentName;
    private String region;
}
