/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerapps.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class ResourceConfiguration {
    public static final List<ResourceConfiguration> CONSUMPTION_CONFIGURATIONS = Arrays.asList(
        ResourceConfiguration.builder().cpu(0.5).memory("1.0Gi").build(),
        ResourceConfiguration.builder().cpu(0.75).memory("1.5Gi").build(),
        ResourceConfiguration.builder().cpu(1.0).memory("2.0Gi").build(),
        ResourceConfiguration.builder().cpu(1.25).memory("2.5Gi").build(),
        ResourceConfiguration.builder().cpu(1.5).memory("3.0Gi").build(),
        ResourceConfiguration.builder().cpu(1.75).memory("3.5Gi").build(),
        ResourceConfiguration.builder().cpu(2.0).memory("4.0Gi").build(),
        ResourceConfiguration.builder().cpu(2.25).memory("4.5Gi").build(),
        ResourceConfiguration.builder().cpu(2.5).memory("5.0Gi").build(),
        ResourceConfiguration.builder().cpu(2.75).memory("5.5Gi").build(),
        ResourceConfiguration.builder().cpu(3.0).memory("6.0Gi").build(),
        ResourceConfiguration.builder().cpu(3.25).memory("6.5Gi").build(),
        ResourceConfiguration.builder().cpu(3.5).memory("7.0Gi").build(),
        ResourceConfiguration.builder().cpu(3.75).memory("7.5Gi").build(),
        ResourceConfiguration.builder().cpu(4.0).memory("8.0Gi").build()
    );

    protected Double cpu;
    protected String memory;
    protected WorkloadProfile workloadProfile;
}
