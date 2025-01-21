/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.sqlserver;

import com.azure.resourcemanager.sql.SqlServerManager;
import com.azure.resourcemanager.sql.models.CapabilityStatus;
import com.azure.resourcemanager.sql.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.sql.models.RegionCapabilities;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzServiceSubscription;
import com.microsoft.azure.toolkit.lib.common.model.Availability;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import lombok.Getter;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
public class MicrosoftSqlServiceSubscription extends AbstractAzServiceSubscription<MicrosoftSqlServiceSubscription, SqlServerManager> {
    @Nonnull
    private final String subscriptionId;
    @Nonnull
    private final MicrosoftSqlServerModule serverModule;

    MicrosoftSqlServiceSubscription(@Nonnull String subscriptionId, @Nonnull AzureSqlServer service) {
        super(subscriptionId, service);
        this.subscriptionId = subscriptionId;
        this.serverModule = new MicrosoftSqlServerModule(this);
    }

    MicrosoftSqlServiceSubscription(@Nonnull SqlServerManager manager, @Nonnull AzureSqlServer service) {
        this(manager.serviceClient().getSubscriptionId(), service);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.singletonList(serverModule);
    }

    @Nonnull
    public MicrosoftSqlServerModule servers() {
        return this.serverModule;
    }

    @Nonnull
    public List<Region> listSupportedRegions() {
        return super.listSupportedRegions(this.serverModule.getName());
    }

    @Nonnull
    public Availability checkNameAvailability(@Nonnull String name) {
        CheckNameAvailabilityResult result = Objects.requireNonNull(this.getRemote()).sqlServers().checkNameAvailability(name);
        return new Availability(result.isAvailable(), result.unavailabilityReason(), result.unavailabilityMessage());
    }

    public boolean checkRegionAvailability(@Nonnull Region region) {
        RegionCapabilities capabilities = Objects.requireNonNull(this.getRemote()).sqlServers()
            .getCapabilitiesByRegion(com.azure.core.management.Region.fromName(region.getName()));

        return Optional.ofNullable(capabilities).map(RegionCapabilities::status).filter(s -> s == CapabilityStatus.AVAILABLE).isPresent();
    }
}

