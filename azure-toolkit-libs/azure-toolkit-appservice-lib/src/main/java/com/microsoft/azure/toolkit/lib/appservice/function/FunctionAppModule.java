/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.function;

import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.Context;
import com.azure.resourcemanager.appservice.AppServiceManager;
import com.azure.resourcemanager.appservice.models.FunctionApps;
import com.azure.resourcemanager.appservice.models.SkuName;
import com.azure.resourcemanager.appservice.models.WebSiteBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.toolkit.lib.appservice.AppServiceResourceModule;
import com.microsoft.azure.toolkit.lib.appservice.AppServiceServiceSubscription;
import com.microsoft.azure.toolkit.lib.appservice.model.FunctionAppLinuxRuntime;
import com.microsoft.azure.toolkit.lib.appservice.model.FunctionAppRuntime;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import com.microsoft.azure.toolkit.lib.containerapps.environment.ContainerAppsEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.microsoft.azure.toolkit.lib.appservice.function.FunctionsServiceSubscription.mapper;
import static com.microsoft.azure.toolkit.lib.appservice.function.FunctionsServiceSubscription.typeRef;

@Slf4j
public class FunctionAppModule extends AppServiceResourceModule<FunctionApp, AppServiceServiceSubscription, com.azure.resourcemanager.appservice.models.FunctionApp> {

    public static final String NAME = "sites";

    public FunctionAppModule(@Nonnull AppServiceServiceSubscription parent) {
        super(NAME, parent);
    }

    @Override
    public FunctionApps getClient() {
        return Optional.ofNullable(this.parent.getRemote()).map(AppServiceManager::functionApps).orElse(null);
    }

    @Nonnull
    @Override
    protected FunctionAppDraft newDraftForCreate(@Nonnull String name, String resourceGroupName) {
        return new FunctionAppDraft(name, resourceGroupName, this);
    }

    @Nonnull
    @Override
    protected FunctionAppDraft newDraftForUpdate(@Nonnull FunctionApp origin) {
        return new FunctionAppDraft(origin);
    }

    @Nonnull
    protected FunctionApp newResource(@Nonnull com.azure.resourcemanager.appservice.models.FunctionApp remote) {
        return new FunctionApp(remote, this);
    }

    @Nonnull
    protected FunctionApp newResource(@Nonnull String name, @Nullable String resourceGroupName) {
        return new FunctionApp(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @Override
    public void delete(@Nonnull final String name, @Nullable final String rgName) {
        final FunctionApp resource = this.get(name, rgName);
        final ContainerAppsEnvironment environment = Optional.ofNullable(resource).map(FunctionApp::getEnvironment).orElse(null);
        super.delete(name, rgName);
        Optional.ofNullable(environment).ifPresent(ContainerAppsEnvironment::refresh);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Function app";
    }

    @Override
    protected List<String> loadResourceIdsFromAzure() {
        return Optional.ofNullable(getClient())
            .map(client -> client.list().stream().map(WebSiteBase::id).collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    }

    public List<Region> listRegions(final PricingTier pricingTier) {
        final SkuName sku = SkuName.fromString(pricingTier.getTier());
        final FunctionApps client = this.getClient();
        if (Objects.isNull(client)) {
            return Collections.emptyList();
        }
        return client.manager().serviceClient().getResourceProviders()
            .listGeoRegions(sku, false, false, false, Context.NONE)
            .stream().map(geoRegionInner -> Region.fromName(geoRegionInner.name()))
            .collect(Collectors.toList());
    }

    public List<? extends FunctionAppRuntime> listFlexConsumptionRuntimes(@Nonnull final Region region) {
        final Map<String, Object> result = getFlexConsumptionRuntimesUsingHttpPipeline(region.getName());
        final List<Map<String, Object>> stacksList = Utils.get(result, "$.value");
        if (CollectionUtils.isEmpty(stacksList)) {
            return Collections.emptyList();
        }
        final List<Map<String, Object>> javaStacks = (List<Map<String, Object>>)stacksList.stream()
            .filter(s -> StringUtils.equalsIgnoreCase(Utils.get(s, "$.name"), "java"))
            .findFirst()
            .map(j -> Utils.get(j, "$.properties.majorVersions"))
            .orElse(Collections.emptyList());
        final List<Map<String, Object>> flexStacks = javaStacks.stream()
            .filter(raw -> {
                    final List<Map<String, Object>> minorVersions = Utils.get(raw, "$.minorVersions");
                    return !CollectionUtils.isEmpty(minorVersions) && minorVersions.stream()
                        .map(v -> Utils.<List<Map<String, Object>>>get(v, "$.stackSettings.linuxRuntimeSettings.Sku"))
                        .filter(Objects::nonNull)
                        .anyMatch(map -> map.stream().anyMatch(m ->
                            StringUtils.equalsIgnoreCase(Utils.<String>get(m, "$.skuCode"), PricingTier.FLEX_CONSUMPTION.getSize())));
                }
            ).collect(Collectors.toList());
        // Currently flex consumption only support linux, skip extract windows values
        // List<FunctionAppWindowsRuntime> windowsRuntimes = FunctionAppWindowsRuntime.getWindowsRuntimeFromMap(javaStacks);
        final List<FunctionAppLinuxRuntime> linuxRuntimes = FunctionAppLinuxRuntime.getLinuxRuntimeFromMap(flexStacks);
        return linuxRuntimes;
    }

    private Map<String, Object> getFlexConsumptionRuntimesUsingHttpPipeline(@Nonnull final String region) {
        final AppServiceManager appServiceManager = Objects.requireNonNull(this.getParent().getRemote());
        final HttpPipeline pipeline = appServiceManager.httpPipeline();
        final HttpRequest request = new HttpRequest(HttpMethod.GET, String.format("%s/providers/Microsoft.Web/locations/%s/functionAppStacks?api-version=2020-10-01&stack=java", appServiceManager.serviceClient().getEndpoint(), region));
        try (final HttpResponse response = pipeline.send(request).block()) {
            if (Objects.nonNull(response) && response.getStatusCode() == 200) {
                final String responseBodyString = response.getBodyAsString().block();
                return mapper.readValue(responseBodyString, typeRef);
            }
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return Collections.emptyMap();
    }
}
