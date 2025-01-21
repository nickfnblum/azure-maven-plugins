package com.microsoft.azure.toolkit.lib.identities;

import com.microsoft.azure.toolkit.lib.identities.model.IdentityConfiguration;

import javax.annotation.Nonnull;

public interface ManagedIdentitySupport {
    IdentityConfiguration getIdentityConfiguration();

    void updateIdentityConfiguration(@Nonnull final IdentityConfiguration configuration);
}
