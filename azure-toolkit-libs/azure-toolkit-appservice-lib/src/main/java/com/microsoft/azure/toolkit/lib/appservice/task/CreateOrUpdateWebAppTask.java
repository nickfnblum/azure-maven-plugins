/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.task;

import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.AzureAppService;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServicePlanConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.DockerConfiguration;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.Runtime;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.service.IAppServicePlan;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebApp;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry;
import com.microsoft.azure.toolkit.lib.resource.task.CreateResourceGroupTask;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CreateOrUpdateWebAppTask extends AzureTask<IWebApp> {
    private static final String CREATE_NEW_WEB_APP = "createNewWebApp";

    private static final String CREATE_WEBAPP = "Creating web app %s...";
    private static final String CREATE_WEB_APP_DONE = "Successfully created Web App %s.";
    private static final String UPDATE_WEBAPP = "Updating target Web App %s...";
    private static final String UPDATE_WEBAPP_DONE = "Successfully updated Web App %s.";

    private final AppServiceConfig config;
    private final List<AzureTask<?>> subTasks;

    public CreateOrUpdateWebAppTask(AppServiceConfig config) {
        this.config = config;
        this.subTasks = this.initTasks();
    }

    private List<AzureTask<?>> initTasks() {
        final List<AzureTask<?>> tasks = new ArrayList<>();
        tasks.add(new CreateResourceGroupTask(this.config.subscriptionId(), this.config.resourceGroup(), this.config.region()));
        if (!StringUtils.equalsIgnoreCase(this.config.servicePlanResourceGroup(), this.config.resourceGroup())) {
            tasks.add(new CreateResourceGroupTask(this.config.subscriptionId(), this.config.servicePlanResourceGroup(), this.config.region()));
        }
        final IAzureMessager messager = AzureMessager.getMessager();
        final String title = String.format("Create new web app(%s)", messager.value(this.config.appName()));

        tasks.add(new AzureTask<>(title, () -> {
            final IWebApp target = Azure.az(AzureAppService.class).subscription(config.subscriptionId())
                .webapp(config.resourceGroup(), config.appName());
            if (!target.exists()) {
                return create();
            }
            return update(target);
        }));
        return tasks;
    }

    private IWebApp create() {
        AzureTelemetry.getActionContext().setProperty(CREATE_NEW_WEB_APP, String.valueOf(true));
        AzureMessager.getMessager().info(String.format(CREATE_WEBAPP, config.appName()));
        final AzureAppService az = Azure.az(AzureAppService.class).subscription(config.subscriptionId());
        final IWebApp webapp = az.webapp(config.resourceGroup(), config.appName());
        final IAppServicePlan appServicePlan = new CreateOrUpdateAppServicePlanTask(toAppServicePlanConfig(config), null).execute();
        final IWebApp result = webapp.create().withName(config.appName())
            .withResourceGroup(config.resourceGroup())
            .withPlan(appServicePlan.id())
            .withRuntime(getRuntime(config.runtime()))
            .withDockerConfiguration(getDockerConfiguration(config.runtime()))
            .withAppSettings(config.appSettings())
            .commit();
        AzureMessager.getMessager().info(String.format(CREATE_WEB_APP_DONE, result.name()));
        return result;
    }

    private static AppServicePlanConfig toAppServicePlanConfig(AppServiceConfig config) {
        return new AppServicePlanConfig()
            .subscriptionId(config.subscriptionId())
            .servicePlanResourceGroup(config.servicePlanResourceGroup())
            .servicePlanName(config.servicePlanName())
            .region(config.region())
            .os(config.runtime() == null ? null : config.runtime().os())
            .pricingTier(config.pricingTier());
    }

    private IWebApp update(final IWebApp webApp) {
        AzureMessager.getMessager().info(String.format(UPDATE_WEBAPP, webApp.name()));
        final IAppServicePlan currentPlan = webApp.plan();
        final IAppServicePlan appServicePlan = new CreateOrUpdateAppServicePlanTask(toAppServicePlanConfig(config), currentPlan).execute();
        final IWebApp result = webApp.update().withPlan(appServicePlan.id())
            .withRuntime(getRuntime(config.runtime()))
            .withDockerConfiguration(getDockerConfiguration(config.runtime()))
            .withAppSettings(ObjectUtils.firstNonNull(config.appSettings(), new HashMap<>()))
            .commit();
        AzureMessager.getMessager().info(String.format(UPDATE_WEBAPP_DONE, webApp.name()));
        return result;
    }

    private DockerConfiguration getDockerConfiguration(RuntimeConfig runtime) {
        if (runtime != null && OperatingSystem.DOCKER == runtime.os()) {
            return DockerConfiguration.builder()
                .userName(runtime.username())
                .password(runtime.password())
                .registryUrl(runtime.registryUrl())
                .image(runtime.image())
                .startUpCommand(runtime.startUpCommand())
                .build();
        }
        return null;

    }

    private Runtime getRuntime(RuntimeConfig runtime) {
        if (runtime != null && OperatingSystem.DOCKER != runtime.os()) {
            return Runtime.getRuntime(runtime.os(),
                runtime.webContainer(),
                runtime.javaVersion());
        } else if (runtime != null && OperatingSystem.DOCKER == runtime.os()) {
            return Runtime.getRuntime(OperatingSystem.DOCKER, WebContainer.JAVA_OFF, JavaVersion.OFF);
        }
        return null;
    }

    @Override
    @AzureOperation(name = "webapp.create_update", params = {"this.config.appName()"}, type = AzureOperation.Type.SERVICE)
    public IWebApp execute() {
        return (IWebApp) Flux.fromStream(this.subTasks.stream().map(t -> t.getSupplier().get())).last().block();
    }
}
