package com.microsoft.azure.toolkit.lib.containerapps.config;

import com.microsoft.azure.toolkit.lib.containerapps.containerapp.ContainerAppDraft;
import com.microsoft.azure.toolkit.lib.containerapps.model.IngressConfig;
import com.microsoft.azure.toolkit.lib.containerapps.model.ResourceConfiguration;
import com.microsoft.azure.toolkit.lib.containerregistry.config.ContainerRegistryConfig;
import lombok.Data;

@Data
public class ContainerAppConfig {
    private ContainerAppsEnvironmentConfig environment;
    private String appName;
    private IngressConfig ingressConfig;
    private ContainerRegistryConfig registryConfig;
    private ContainerAppDraft.ImageConfig imageConfig;
    private ResourceConfiguration resourceConfiguration;
    private ContainerAppDraft.ScaleConfig scaleConfig;
}
