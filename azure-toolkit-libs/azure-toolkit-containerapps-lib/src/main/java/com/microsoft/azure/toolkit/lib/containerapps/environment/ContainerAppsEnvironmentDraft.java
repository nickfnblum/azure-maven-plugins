/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerapps.environment;

import com.azure.resourcemanager.appcontainers.models.AppLogsConfiguration;
import com.azure.resourcemanager.appcontainers.models.LogAnalyticsConfiguration;
import com.azure.resourcemanager.appcontainers.models.ManagedEnvironment;
import com.azure.resourcemanager.appcontainers.models.ManagedEnvironments;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.action.AzureActionManager;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.AzResource;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.containerapps.model.EnvironmentType;
import com.microsoft.azure.toolkit.lib.containerapps.model.WorkloadProfile;
import com.microsoft.azure.toolkit.lib.monitor.LogAnalyticsWorkspace;
import com.microsoft.azure.toolkit.lib.monitor.LogAnalyticsWorkspaceDraft;
import com.microsoft.azure.toolkit.lib.resource.ResourceGroup;
import com.microsoft.azure.toolkit.lib.resource.ResourceGroupDraft;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ContainerAppsEnvironmentDraft extends ContainerAppsEnvironment implements AzResource.Draft<ContainerAppsEnvironment, ManagedEnvironment> {
    @Getter
    private final ContainerAppsEnvironment origin;

    @Setter
    @Getter
    private Config config;

    protected ContainerAppsEnvironmentDraft(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull ContainerAppsEnvironmentModule module) {
        super(name, resourceGroupName, module);
        this.origin = null;
    }

    public ContainerAppsEnvironmentDraft(@Nonnull ContainerAppsEnvironment origin) {
        super(origin);
        this.origin = origin;
    }

    @Override
    public void reset() {
        this.config = null;
    }

    @Nonnull
    @Override
    @AzureOperation(name = "azure/containerapps.create_environment.env", params = {"this.getName()"})
    public ManagedEnvironment createResourceInAzure() {
        final IAzureMessager messager = AzureMessager.getMessager();
        final ManagedEnvironments client = Objects.requireNonNull(((ContainerAppsEnvironmentModule) getModule()).getClient());
        final Config config = ensureConfig();
        final AppLogsConfiguration appLogsConfiguration = new AppLogsConfiguration();
        final LogAnalyticsWorkspace logAnalyticsWorkspace = config.getLogAnalyticsWorkspace();
        if (Objects.nonNull(logAnalyticsWorkspace)) {
            if (logAnalyticsWorkspace.isDraftForCreating()) {
                ((LogAnalyticsWorkspaceDraft) logAnalyticsWorkspace).createIfNotExist();
            }
            final LogAnalyticsConfiguration analyticsConfiguration = new LogAnalyticsConfiguration()
                    .withCustomerId(logAnalyticsWorkspace.getCustomerId())
                    .withSharedKey(logAnalyticsWorkspace.getPrimarySharedKeys());
            appLogsConfiguration.withDestination("log-analytics").withLogAnalyticsConfiguration(analyticsConfiguration);
        }
        messager.info(AzureString.format("Start creating Azure Container Apps Environment({0})...", this.getName()));
        final EnvironmentType environmentType = getEnvironmentType();
        // if users did not set environment type,
        final List<com.azure.resourcemanager.appcontainers.models.WorkloadProfile> workloadProfiles = environmentType == EnvironmentType.ConsumptionOnly ? null :
            Objects.requireNonNull(getWorkloadProfiles()).stream().map(WorkloadProfile::toWorkloadProfile).collect(Collectors.toList());
        if (Objects.nonNull(workloadProfiles) && workloadProfiles.stream().noneMatch(profile -> StringUtils.equalsIgnoreCase(profile.workloadProfileType(), WorkloadProfile.CONSUMPTION))) {
            workloadProfiles.add(WorkloadProfile.toWorkloadProfile(WorkloadProfile.CONSUMPTION_PROFILE));
        }
        final ManagedEnvironment managedEnvironment = client.define(config.getName())
                .withRegion(com.azure.core.management.Region.fromName(config.getRegion().getName()))
                .withExistingResourceGroup(Objects.requireNonNull(config.getResourceGroup(), "Resource Group is required to create Container app.").getResourceGroupName())
                .withAppLogsConfiguration(appLogsConfiguration)
                .withWorkloadProfiles(workloadProfiles).create();
        final Action<ContainerAppsEnvironment> create = Optional.ofNullable(AzureActionManager.getInstance().getAction(CREATE_CONTAINER_APP))
            .map(action -> action.bind(this).withLabel("Create app")).orElse(null);
        messager.success(AzureString.format("Azure Container Apps Environment({0}) is successfully created.", this.getName()), create);
        return managedEnvironment;
    }

    @Nullable
    @Override
    public Region getRegion() {
        return Optional.ofNullable(config).map(Config::getRegion).orElseGet(super::getRegion);
    }

    @Nullable
    @Override
    public EnvironmentType getEnvironmentType() {
        return Optional.ofNullable(config).map(Config::getEnvironmentType).orElseGet(super::getEnvironmentType);
    }

    @Nullable
    @Override
    public List<WorkloadProfile> getWorkloadProfiles() {
        return Optional.ofNullable(config).map(Config::getWorkloadProfiles).orElseGet(super::getWorkloadProfiles);
    }

    @Override
    public ResourceGroup getResourceGroup() {
        final ResourceGroup rg = Optional.ofNullable(config).map(Config::getResourceGroup).orElseGet(super::getResourceGroup);
        if (Objects.nonNull(rg) && rg.isDraftForCreating() && Objects.isNull(rg.getRegion())) {
            ((ResourceGroupDraft) rg).setRegion(this.getRegion());
        }
        return rg;
    }

    @Nonnull
    @Override
    public ManagedEnvironment updateResourceInAzure(@Nonnull ManagedEnvironment origin) {
        throw new UnsupportedOperationException("not support");
    }

    @Override
    public boolean isModified() {
        return this.config != null && !Objects.equals(config, new Config());
    }

    @Nonnull
    private synchronized Config ensureConfig() {
        this.config = Optional.ofNullable(this.config).orElseGet(ContainerAppsEnvironmentDraft.Config::new);
        return this.config;
    }

    @Data
    public static class Config {
        private String name;
        private Subscription subscription;
        private ResourceGroup resourceGroup;
        private Region region;
        private LogAnalyticsWorkspace logAnalyticsWorkspace;
        // workload profile configuration
        private EnvironmentType environmentType;
        private List<WorkloadProfile> workloadProfiles;
    }
}
