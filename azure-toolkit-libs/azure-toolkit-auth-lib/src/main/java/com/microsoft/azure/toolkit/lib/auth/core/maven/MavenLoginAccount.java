/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.maven;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.implementation.util.IdentityConstants;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.MasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenMasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collections;

public class MavenLoginAccount extends Account {
    @Getter
    private final AuthMethod method = AuthMethod.AZURE_SECRET_FILE;

    private AzureCredential mavenCredentials;

    public MavenLoginAccount() {
        if (MavenLoginHelper.existsAzureSecretFile()) {
            try {
                mavenCredentials = MavenLoginHelper.readAzureCredentials(MavenLoginHelper.getAzureSecretFile());
            } catch (IOException e) {
                // ignore error
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return this.mavenCredentials != null && StringUtils.isNotBlank(this.mavenCredentials.getRefreshToken());
    }

    @Override
    public void initializeCredentials() throws LoginFailureException {
        String envString = mavenCredentials.getEnvironment();
        AzureEnvironment environment = ObjectUtils.firstNonNull(AzureEnvironmentUtils.stringToAzureEnvironment(envString), AzureEnvironment.AZURE);
        entity.setEnvironment(environment);

        if (StringUtils.isBlank(mavenCredentials.getRefreshToken())) {
            throw new LoginFailureException("Missing required 'refresh_token' from file:" + MavenLoginHelper.getAzureSecretFile());
        }

        entity.setSelectedSubscriptionIds(Collections.singletonList(mavenCredentials.getDefaultSubscription()));
        if (mavenCredentials.getUserInfo() != null) {
            entity.setEmail(mavenCredentials.getUserInfo().getDisplayableId());
        }
        MasterTokenCredential oauthMasterTokenCredential =
                new RefreshTokenMasterTokenCredential(environment, IdentityConstants.DEVELOPER_SINGLE_SIGN_ON_ID, this.mavenCredentials.getRefreshToken());
        entity.setCredential(oauthMasterTokenCredential);
    }
}
