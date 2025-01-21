/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.identities.model;

import com.microsoft.azure.toolkit.lib.identities.Identity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityConfiguration {
    // system assigned identity
    private boolean enableSystemAssignedManagedIdentity;
    private String tenantId;
    private String principalId;
    // user assigned identities
    private List<Identity> userAssignedManagedIdentities;
}
