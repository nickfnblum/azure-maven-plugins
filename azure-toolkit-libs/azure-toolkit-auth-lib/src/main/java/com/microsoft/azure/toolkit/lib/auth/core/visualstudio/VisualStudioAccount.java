/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth.core.visualstudio;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.SharedTokenCacheCredential;
import com.azure.identity.SharedTokenCacheCredentialBuilder;
import com.azure.identity.implementation.IdentityClient;
import com.azure.identity.implementation.MsalToken;
import com.azure.identity.implementation.SynchronizedAccessor;
import com.azure.identity.implementation.util.ScopeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.TokenCache;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.MasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.core.refresktoken.RefreshTokenMasterTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.exception.AzureToolkitAuthenticationException;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AuthMethod;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class VisualStudioAccount extends Account {
    private static final String VISUAL_STUDIO_CLIENT_ID = "872cd9fa-d31f-45e0-9eab-6e460a02d1f1";
    @Getter
    private final AuthMethod method = AuthMethod.VISUAL_STUDIO;

    private AzureEnvironment environment;

    private Optional<Pair<AzureEnvironment, CachedAccountEntity>> vsAccount;


    public VisualStudioAccount(AzureEnvironment environment) {
        this.environment = environment;
        try {
            loadVisualStudioAccounts();
        } catch (IllegalAccessException | JsonProcessingException | ExecutionException | InterruptedException e) {
            throw new AzureToolkitAuthenticationException(e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return vsAccount.isPresent();
    }

    @Override
    public void initializeCredentials() throws LoginFailureException {
        entity.setEnvironment(vsAccount.get().getKey());
        this.environment = entity.getEnvironment();
        entity.setEmail(vsAccount.get().getValue().getUsername());
        SharedTokenCacheCredential vsCredential = new SharedTokenCacheCredentialBuilder().clientId(VISUAL_STUDIO_CLIENT_ID)
                .tenantId(null).username(entity.getEmail()).build();
        AccessToken accessToken = vsCredential.getToken(new TokenRequestContext()
                .addScopes(ScopeUtil.resourceToScopes(entity.getEnvironment().getManagementEndpoint()))).block();

        // legacy code will be removed after https://github.com/jongio/azidext/pull/41 is merged
        IAuthenticationResult result = ((MsalToken) accessToken).getAuthenticationResult();
        if (result != null && result.account() != null) {
            this.entity.setEmail(result.account().username());
        }
        String refreshToken;
        try {
            refreshToken = (String) FieldUtils.readField(result, "refreshToken", true);
        } catch (IllegalAccessException e) {
            throw new LoginFailureException("Cannot read refreshToken from Visual Studio shared token pools.");
        }
        if (StringUtils.isBlank(refreshToken)) {
            throw new LoginFailureException("Cannot read refreshToken from Visual Studio shared token pools.");
        }

        MasterTokenCredential oauthMasterTokenCredential =
                new RefreshTokenMasterTokenCredential(environment, VISUAL_STUDIO_CLIENT_ID, refreshToken);
        entity.setCredential(oauthMasterTokenCredential);
    }

    private void loadVisualStudioAccounts()
            throws IllegalAccessException, JsonProcessingException, ExecutionException, InterruptedException {
        Map<String, AzureEnvironment> envEndpoints = Utils.groupByIgnoreDuplicate(
                environment != null ? Collections.singletonList(environment) : AzureEnvironment.knownEnvironments(), AzureEnvironment::getManagementEndpoint);

        SharedTokenCacheCredential cred = new SharedTokenCacheCredentialBuilder().clientId(VISUAL_STUDIO_CLIENT_ID).username("test-account").build();
        IdentityClient identityClient = (IdentityClient) FieldUtils.readField(cred, "identityClient", true);
        SynchronizedAccessor<PublicClientApplication> publicClientApplicationAccessor = (SynchronizedAccessor<PublicClientApplication>)
                FieldUtils.readField(identityClient, "publicClientApplicationAccessor", true);
        TokenCache tc = publicClientApplicationAccessor.getValue().block().tokenCache();
        publicClientApplicationAccessor.getValue().block().getAccounts().get();
        TokenCacheEntity tokenCacheEntity = convertByJson(tc, TokenCacheEntity.class);
        final Map<String, CachedAccountEntity> accountMap = Utils.groupByIgnoreDuplicate(
                tokenCacheEntity.getAccounts().values(), CachedAccountEntity::getHomeAccountId);
        Set<Pair<AzureEnvironment, CachedAccountEntity>> sharedAccounts = new HashSet<>();
        tokenCacheEntity.getAccessTokens().values().stream().forEach(refreshTokenCache -> {
            if (StringUtils.equalsIgnoreCase(refreshTokenCache.getClientId(), VISUAL_STUDIO_CLIENT_ID)) {
                CachedAccountEntity accountCache = accountMap.get(refreshTokenCache.getHomeAccountId());
                Optional<String> envKey = envEndpoints.keySet().stream().filter(q -> refreshTokenCache.getTarget().startsWith(q)).findFirst();
                if (envKey.isPresent() && accountCache != null) {
                    if (this.environment != null && this.environment != envEndpoints.get(envKey.get())) {
                        // if env is specified, we need to ignore the accounts on other environments
                        return;
                    }
                    sharedAccounts.add(Pair.of(envEndpoints.get(envKey.get()), accountCache));
                }
            }
        });

        // where there are multiple accounts, we will prefer azure global accounts
        vsAccount = sharedAccounts.stream()
                .filter(accountInCache -> accountInCache.getKey() == AzureEnvironment.AZURE).findFirst();
        // TODO: add username in AuthConfiguration for selecting accounts in Visual Studio credentials
        if (!vsAccount.isPresent()) {
            // where there are multiple non-global accounts, select any of them
            vsAccount = sharedAccounts.stream().findFirst();
        }
    }

    private static <T> T convertByJson(Object from, Class<T> toClass) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = objectMapper.writeValueAsString(from);
        return objectMapper.readValue(json, toClass);
    }
}
