/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.vscode;

import com.azure.core.management.AzureEnvironment;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.MasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenMasterTokenCredential;
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

public class VisualStudioCodeAccount extends Account {
    private static final String VSCODE_CLIENT_ID = "aebc6443-996d-45c2-90f0-388ff96faa56";
    @Getter
    private final AuthMethod method = AuthMethod.VSCODE;
    private Map<String, String> vscodeUserSettings;
    private String refreshToken;
    private String vscodeCloudName;

    public VisualStudioCodeAccount() {
        VisualStudioCacheAccessor accessor = new VisualStudioCacheAccessor();
        vscodeUserSettings = accessor.getUserSettingsDetails();
        vscodeCloudName = vscodeUserSettings.get("cloud");
        refreshToken = accessor.getCredentials("VS Code Azure", vscodeCloudName);
    }

    public boolean isAvailable() {
        try {
            return StringUtils.isNotEmpty(refreshToken);
        } catch (Throwable ex) {
            this.entity.setLastError(ex);
            return false;
        }
    }

    @Override
    public void initializeCredentials() throws LoginFailureException {
        List<String> filteredSubscriptions;
        if (vscodeUserSettings.containsKey("filter")) {
            filteredSubscriptions = Arrays.asList(StringUtils.split(vscodeUserSettings.get("filter"), ","));
        } else {
            filteredSubscriptions = new ArrayList<>();
        }
        AzureEnvironment environment = ObjectUtils.firstNonNull(AzureEnvironmentUtils.stringToAzureEnvironment(vscodeCloudName), AzureEnvironment.AZURE);
        this.entity.setEnvironment(environment);

        if (StringUtils.isEmpty(refreshToken)) {
            throw new LoginFailureException("Cannot get credentials from VSCode, please make sure that you have signed-in in VSCode Azure Account plugin");
        }
        this.entity.setSelectedSubscriptionIds(filteredSubscriptions);
        MasterTokenCredential oauthMasterTokenCredential =
                new RefreshTokenMasterTokenCredential(environment, VSCODE_CLIENT_ID, refreshToken);
        entity.setCredential(oauthMasterTokenCredential);
    }
}
