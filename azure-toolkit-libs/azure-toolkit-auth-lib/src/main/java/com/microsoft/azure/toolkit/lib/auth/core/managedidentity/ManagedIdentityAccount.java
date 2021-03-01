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
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class ManagedIdentityAccount extends Account {
    @Getter
    private final AuthMethod method = AuthMethod.MANAGED_IDENTITY;

    @Getter
    private AzureEnvironment environment;

    @Override
    public void initializeCredentials() {
        AzureEnvironmentUtils.setupAzureEnvironment(environment);
        ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder().build();
        this.entity.setCredential(new DefaultTokenCredential(environment, managedIdentityCredential));
    }
}
