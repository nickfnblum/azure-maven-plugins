/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.identities;

import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class Identity extends AbstractAzResource<Identity, AzureManagedIdentitySubscription, com.azure.resourcemanager.msi.models.Identity> implements Deletable {

    protected Identity(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull AzureManagedIdentityModule module) {
        super(name, resourceGroupName, module);
    }

    protected Identity(@Nonnull com.azure.resourcemanager.msi.models.Identity remote, @Nonnull AzureManagedIdentityModule module) {
        super(remote.name(), ResourceId.fromString(remote.id()).resourceGroupName(), module);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull com.azure.resourcemanager.msi.models.Identity remote) {
        // todo: get status of identity
        return Status.RUNNING;
    }

    @Nullable
    public String getTenantId(){
        return remoteOptional().map(com.azure.resourcemanager.msi.models.Identity::tenantId).orElse(null);
    }

    @Nullable
    public String getPrincipalId(){
        return remoteOptional().map(com.azure.resourcemanager.msi.models.Identity::principalId).orElse(null);
    }

    @Nullable
    public String getClientId(){
        return remoteOptional().map(com.azure.resourcemanager.msi.models.Identity::clientId).orElse(null);
    }
    // list all role assignments
    // list role assignments for a specific resource
}

