/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.vscode;

import com.azure.core.management.AzureEnvironment;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenAccount;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.auth.util.AzureEnvironmentUtils;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class VisualStudioCodeAccount extends RefreshTokenAccount {
    private static final String VSCODE_CLIENT_ID = "aebc6443-996d-45c2-90f0-388ff96faa56";
    @Getter
    private final AuthMethod method = AuthMethod.VSCODE;
    private Map<String, String> vscodeUserSettings;

    public VisualStudioCodeAccount() {
        clientId = VSCODE_CLIENT_ID;
    }

    @Override
    public void initializeCredentials() throws LoginFailureException {
        this.entity.setEnvironment(environment);
        List<String> filteredSubscriptions;
        if (vscodeUserSettings.containsKey("filter")) {
            filteredSubscriptions = Arrays.asList(StringUtils.split(vscodeUserSettings.get("filter"), ","));
        } else {
            filteredSubscriptions = new ArrayList<>();
        }
        this.entity.setSelectedSubscriptionIds(filteredSubscriptions);

        super.initializeCredentials();

    }

    @Override
    protected void initializeRefreshToken() {
        VisualStudioCacheAccessor accessor = new VisualStudioCacheAccessor();
        vscodeUserSettings = accessor.getUserSettingsDetails();
        String vscodeCloudName = vscodeUserSettings.get("cloud");
        environment = ObjectUtils.firstNonNull(AzureEnvironmentUtils.stringToAzureEnvironment(vscodeCloudName), AzureEnvironment.AZURE);
        refreshToken = accessor.getCredentials("VS Code Azure", vscodeCloudName);
    }
}
