/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.maven;
import com.azure.core.management.AzureEnvironment;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenAccount;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.io.IOException;
import java.util.Collections;

public class MavenLoginAccount extends RefreshTokenAccount {
    @Getter
    private final AuthMethod method = AuthMethod.AZURE_SECRET_FILE;

    private AzureCredential mavenCredentials;

    @Override
    protected void initializeRefreshToken() {
        if (MavenLoginHelper.existsAzureSecretFile()) {
            try {
                mavenCredentials = MavenLoginHelper.readAzureCredentials(MavenLoginHelper.getAzureSecretFile());
                refreshToken = mavenCredentials != null ? mavenCredentials.getRefreshToken() : null;
            } catch (IOException e) {
                // ignore error
            }
        }
    }

    @Override
    protected void initializeCredentials() throws LoginFailureException {
        String envString = mavenCredentials.getEnvironment();
        environment = ObjectUtils.firstNonNull(AzureEnvironmentUtils.stringToAzureEnvironment(envString), AzureEnvironment.AZURE);
        entity.setEnvironment(environment);
        entity.setSelectedSubscriptionIds(Collections.singletonList(mavenCredentials.getDefaultSubscription()));
        if (mavenCredentials.getUserInfo() != null) {
            entity.setEmail(mavenCredentials.getUserInfo().getDisplayableId());
        }
        super.initializeCredentials();
    }
}
