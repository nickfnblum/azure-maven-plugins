/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerapps;

import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzServiceSubscription;
import com.microsoft.azure.toolkit.lib.containerapps.containerapp.ContainerAppModule;
import com.microsoft.azure.toolkit.lib.containerapps.environment.ContainerAppsEnvironmentModule;
import com.microsoft.azure.toolkit.lib.containerapps.model.WorkloadProfileType;
import lombok.Getter;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AzureContainerAppsServiceSubscription extends AbstractAzServiceSubscription<AzureContainerAppsServiceSubscription, ContainerAppsApiManager> {
    @Nonnull
    @Getter
    private final String subscriptionId;
    private final ContainerAppModule containerAppModule;
    private final ContainerAppsEnvironmentModule environmentModule;

    protected AzureContainerAppsServiceSubscription(@Nonnull String subscriptionId, @Nonnull AbstractAzResourceModule<AzureContainerAppsServiceSubscription, None, ContainerAppsApiManager> module) {
        super(subscriptionId, module);
        this.subscriptionId = subscriptionId;
        this.containerAppModule = new ContainerAppModule(this);
        this.environmentModule = new ContainerAppsEnvironmentModule(this);
    }

    public ContainerAppModule containerApps() {
        return this.containerAppModule;
    }

    public ContainerAppsEnvironmentModule environments() {
        return this.environmentModule;
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Arrays.asList(containerAppModule, environmentModule);
    }

    public List<WorkloadProfileType> listAvailableWorkloadProfiles(@Nonnull final String region) {
        return remoteOptional()
            .map(c -> c.availableWorkloadProfiles().get(region).stream().map(WorkloadProfileType::fromAvailableProfile).collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    }
}
