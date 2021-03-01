/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth;

import com.microsoft.azure.toolkit.lib.auth.exception.AzureLoginException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;

public interface IAuthenticator {
    AuthMethod getAuthMethod();

    boolean isAvailable();

    void authenticate() throws AzureLoginException;

}
