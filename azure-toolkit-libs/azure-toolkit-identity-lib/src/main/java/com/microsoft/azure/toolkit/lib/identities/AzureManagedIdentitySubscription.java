/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.identities;

import com.azure.resourcemanager.msi.MsiManager;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzServiceSubscription;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import lombok.Getter;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

@Getter
public class AzureManagedIdentitySubscription extends AbstractAzServiceSubscription<AzureManagedIdentitySubscription, MsiManager> {
    @Nonnull
    private final String subscriptionId;
    @Nonnull
    private final AzureManagedIdentityModule identityModule;


    protected AzureManagedIdentitySubscription(@Nonnull String subscriptionId, @Nonnull AzureManagedIdentity service) {
        super(subscriptionId, service);
        this.subscriptionId = subscriptionId;
        this.identityModule = new AzureManagedIdentityModule(this);
    }

    protected AzureManagedIdentitySubscription(@Nonnull MsiManager manager, @Nonnull AzureManagedIdentity service) {
        this(manager.serviceClient().getSubscriptionId(), service);
    }

    public AzureManagedIdentityModule identity() {
        return this.identityModule;
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.singletonList(identityModule);
    }

    @Override
    public List<Region> listSupportedRegions(@Nonnull String resourceType) {
        return super.listSupportedRegions(this.identityModule.getName());
    }
}