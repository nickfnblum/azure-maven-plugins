/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.serviceprincipal;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.ClientCertificateCredentialBuilder;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.DefaultTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthConfiguration;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import java.util.Objects;

public class ServicePrincipalAccount extends Account {
    @Getter
    private final AuthMethod method = AuthMethod.SERVICE_PRINCIPAL;
    private AuthConfiguration configuration;
    private TokenCredential clientSecretCredential;

    public ServicePrincipalAccount(@Nonnull AuthConfiguration authConfiguration) {
        Objects.requireNonNull(authConfiguration);
        this.configuration = authConfiguration;
    }

    @Override
    public boolean checkAvailable() {
        try {
            AzureEnvironmentUtils.setupAzureEnvironment(configuration.getEnvironment());
            clientSecretCredential = StringUtils.isNotBlank(configuration.getCertificate()) ?
                    new ClientCertificateCredentialBuilder().clientId(configuration.getClient())
                            .pfxCertificate(configuration.getCertificate(), configuration.getCertificatePassword())
                            .tenantId(configuration.getTenant()).build()
                    : new ClientSecretCredentialBuilder().clientId(configuration.getClient())
                    .clientSecret(configuration.getKey()).tenantId(configuration.getTenant()).build();
            verifyTokenCredential(ObjectUtils.firstNonNull(configuration.getEnvironment(), AzureEnvironment.AZURE), clientSecretCredential);
            return true;
        } catch (Throwable ex) {
            this.entity.setLastError(ex);
            return false;
        }
    }

    @Override
    public void initializeCredentials() throws LoginFailureException {
        this.entity.setEnvironment(ObjectUtils.firstNonNull(configuration.getEnvironment(), AzureEnvironment.AZURE));
        this.entity.setCredential(new DefaultTokenCredential(this.entity.getEnvironment(), clientSecretCredential));
    }
}
