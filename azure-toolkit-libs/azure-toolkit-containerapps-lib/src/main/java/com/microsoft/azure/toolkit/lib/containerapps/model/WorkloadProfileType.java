/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerapps.model;

import com.azure.resourcemanager.appcontainers.fluent.models.AvailableWorkloadProfileInner;
import com.azure.resourcemanager.appcontainers.models.AvailableWorkloadProfile;
import com.azure.resourcemanager.appcontainers.models.AvailableWorkloadProfileProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Nonnull;
import java.util.Optional;

@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode
public class WorkloadProfileType {
    public static final WorkloadProfileType CONSUMPTION_TYPE = WorkloadProfileType.builder().name(WorkloadProfile.CONSUMPTION).build();

    private final String name;
    private final String category;
    private final String displayName;
    private final Integer cores;
    private final Integer memory;

    public static WorkloadProfileType fromAvailableProfile(@Nonnull final AvailableWorkloadProfile profile) {
        final AvailableWorkloadProfileProperties properties = Optional.ofNullable(profile.innerModel())
            .map(AvailableWorkloadProfileInner::properties).orElse(null);
        return WorkloadProfileType.builder()
            .name(profile.name())
            .category(Optional.ofNullable(properties).map(AvailableWorkloadProfileProperties::category).orElse(null))
            .displayName(Optional.ofNullable(properties).map(AvailableWorkloadProfileProperties::displayName).orElse(null))
            .cores(Optional.ofNullable(properties).map(AvailableWorkloadProfileProperties::cores).orElse(null))
            .memory(Optional.ofNullable(properties).map(AvailableWorkloadProfileProperties::memoryGiB).orElse(null)).build();
    }
}
