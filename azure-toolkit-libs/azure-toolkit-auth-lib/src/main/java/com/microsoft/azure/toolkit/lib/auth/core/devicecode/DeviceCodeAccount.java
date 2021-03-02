/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.devicecode;

import com.azure.core.management.AzureEnvironment;
import com.azure.identity.DeviceCodeCredential;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenAccount;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import com.microsoft.azure.toolkit.lib.common.utils.TextUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;

public class DeviceCodeAccount extends RefreshTokenAccount {
    private static final String AZURE_TOOLKIT_CLIENT_ID = "777acee8-5286-4d6e-8b05-f7c851d8ed0a";
    @Getter
    private final AuthMethod method = AuthMethod.DEVICE_CODE;

    public DeviceCodeAccount(@Nonnull AzureEnvironment environment) {
        this.environment = environment;
        clientId = AZURE_TOOLKIT_CLIENT_ID;
    }

    @Override
    protected void initializeRefreshToken() {
        // make the checkAvailable always returns true
        this.refreshToken = "dummy";
    }

    @Override
    protected void initializeCredentials() throws LoginFailureException {
        AzureEnvironmentUtils.setupAzureEnvironment(environment);
        DeviceCodeCredential deviceCodeCredential = new DeviceCodeCredentialBuilder().clientId(AZURE_TOOLKIT_CLIENT_ID)
                .challengeConsumer(challenge -> System.out.println(StringUtils.replace(challenge.getMessage(), challenge.getUserCode(),
                        TextUtils.cyan(challenge.getUserCode())))).build();

        initializeFromTokenCredential(deviceCodeCredential);
    }

}
