/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.model;

import com.azure.resourcemanager.appservice.fluent.models.SiteInner;
import com.azure.resourcemanager.appservice.models.FunctionApp;
import com.azure.resourcemanager.appservice.models.ResourceConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.annotation.Nonnull;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class ContainerAppFunctionConfiguration {
    protected Integer minReplicas;
    protected Integer maxReplicas;
    // workload profile properties
    protected Double cpu;
    protected String memory;
    protected String workloadProfileMame;

    public static ContainerAppFunctionConfiguration fromFunctionApp(@Nonnull final FunctionApp app) {
        final String workloadProfile = Optional.ofNullable(app.innerModel()).map(SiteInner::workloadProfileName).orElse(null);
        final ResourceConfig resourceConfig = Optional.ofNullable(app.innerModel()).map(SiteInner::resourceConfig).orElse(null);
        final Double cpu = Optional.ofNullable(resourceConfig).map(ResourceConfig::cpu).orElse(null);
        final String memory = Optional.ofNullable(resourceConfig).map(ResourceConfig::memory).orElse(null);
        return ContainerAppFunctionConfiguration.builder()
            .minReplicas(app.minReplicas())
            .minReplicas(app.maxReplicas())
            .workloadProfileMame(workloadProfile)
            .cpu(cpu)
            .memory(memory)
            .build();
    }
}
