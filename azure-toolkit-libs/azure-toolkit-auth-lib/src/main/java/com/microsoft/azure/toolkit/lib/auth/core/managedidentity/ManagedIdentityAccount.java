/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.managedidentity;

import com.azure.core.management.AzureEnvironment;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.DefaultTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import lombok.Getter;

import javax.annotation.Nonnull;

public class ManagedIdentityAccount extends Account {
    @Getter
    private final AuthMethod method = AuthMethod.MANAGED_IDENTITY;

    private ManagedIdentityCredential managedIdentityCredential;

    @Getter
    private AzureEnvironment environment;

    public ManagedIdentityAccount(@Nonnull AzureEnvironment environment) {
        this.environment = environment;
        AzureEnvironmentUtils.setupAzureEnvironment(environment);
        managedIdentityCredential = new ManagedIdentityCredentialBuilder().build();
    }

    @Override
    protected boolean checkAvailable() {
        try {
            verifyTokenCredential(this.environment, managedIdentityCredential);
            return true;
        } catch (LoginFailureException e) {
            this.entity.setLastError(e);
        }
        return false;
    }

    @Override
    protected void initializeCredentials() {
        this.entity.setCredential(new DefaultTokenCredential(environment, managedIdentityCredential));
    }
}
