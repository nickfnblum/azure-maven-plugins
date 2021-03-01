/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.visualstudio;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
class TokenCacheEntity {
    @JsonProperty("AccessToken")
    Map<String, CachedAccessTokenEntity> accessTokens = new LinkedHashMap<>();

    @JsonProperty("Account")
    Map<String, CachedAccountEntity> accounts = new LinkedHashMap<>();
}
