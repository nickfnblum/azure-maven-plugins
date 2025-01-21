/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.identities;

import com.azure.core.util.paging.ContinuablePage;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identities;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class AzureManagedIdentityModule extends AbstractAzResourceModule<Identity, AzureManagedIdentitySubscription, com.azure.resourcemanager.msi.models.Identity> {
    public static final String NAME = "userAssignedIdentities";

    public AzureManagedIdentityModule(@Nonnull final AzureManagedIdentitySubscription parent) {
        super(NAME, parent);
    }

    @Nonnull
    @Override
    protected Iterator<? extends ContinuablePage<String, com.azure.resourcemanager.msi.models.Identity>> loadResourcePagesFromAzure() {
        return Optional.ofNullable(this.getClient()).map(c -> c.list().iterableByPage(getPageSize()).iterator()).orElse(Collections.emptyIterator());
    }

    @Nullable
    @Override
    @AzureOperation(name = "azure/eventhubs.load_event_hubs_namespace.eventhubs", params = {"name"})
    protected com.azure.resourcemanager.msi.models.Identity loadResourceFromAzure(@Nonnull String name, @Nullable String resourceGroup) {
        assert StringUtils.isNoneBlank(resourceGroup) : "resource group can not be empty";
        return Optional.ofNullable(this.getClient()).map(eventHubNamespaces -> eventHubNamespaces.getByResourceGroup(resourceGroup, name)).orElse(null);
    }

    @Override
    @AzureOperation(name = "azure/eventhubs.delete_event_hubs_namespace.eventhubs", params = {"nameFromResourceId(resourceId)"})
    protected void deleteResourceFromAzure(@Nonnull String resourceId) {
        Optional.ofNullable(this.getClient()).ifPresent(eventHubNamespaces -> eventHubNamespaces.deleteById(resourceId));
    }

    @Nonnull
    @Override
    protected Identity newResource(@Nonnull com.azure.resourcemanager.msi.models.Identity remote) {
        return new Identity(remote, this);
    }

    @Nonnull
    @Override
    protected Identity newResource(@Nonnull String name, @Nullable String resourceGroupName) {
        return new Identity(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @Nullable
    @Override
    protected Identities getClient() {
        return Optional.ofNullable(this.parent.getRemote()).map(MsiManager::identities).orElse(null);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Managed Identities";
    }
}

