/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.devicecode;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.azure.identity.implementation.MsalToken;
import com.azure.identity.implementation.util.IdentityConstants;
import com.azure.identity.implementation.util.ScopeUtil;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.MasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenMasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
@AllArgsConstructor
public class DeviceCodeAccount extends Account {
    private static final String AZURE_TOOLKIT_CLIENT_ID = "777acee8-5286-4d6e-8b05-f7c851d8ed0a";
    @Getter
    private final AuthMethod method = AuthMethod.DEVICE_CODE;

    @Getter
    private AzureEnvironment environment;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void initializeCredentials() throws LoginFailureException {
        AzureEnvironmentUtils.setupAzureEnvironment(environment);
        DeviceCodeCredential deviceCodeCredential = new DeviceCodeCredentialBuilder().clientId(AZURE_TOOLKIT_CLIENT_ID)
                .challengeConsumer(challenge -> System.out.println(StringUtils.replace(challenge.getMessage(), challenge.getUserCode(),
                        TextUtils.cyan(challenge.getUserCode())))).build();

        AccessToken accessToken = deviceCodeCredential.getToken(new TokenRequestContext()
                .addScopes(ScopeUtil.resourceToScopes(environment.getManagementEndpoint()))).block();

        // legacy code will be removed after https://github.com/jongio/azidext/pull/41 is merged
        IAuthenticationResult result = ((MsalToken) accessToken).getAuthenticationResult();
        if (result != null && result.account() != null) {
            entity.setEmail(result.account().username());
        }
        String refreshToken;
        try {
            refreshToken = (String) FieldUtils.readField(result, "refreshToken", true);
        } catch (IllegalAccessException e) {
            throw new LoginFailureException("Cannot read refreshToken from DeviceCodeCredential.");
        }
        if (StringUtils.isBlank(refreshToken)) {
            throw new LoginFailureException("Cannot get refresh token from device code workflow.");
        }

        MasterTokenCredential oauthMasterTokenCredential =
                new RefreshTokenMasterTokenCredential(environment, IdentityConstants.DEVELOPER_SINGLE_SIGN_ON_ID, refreshToken);
        entity.setCredential(oauthMasterTokenCredential);
    }

}
