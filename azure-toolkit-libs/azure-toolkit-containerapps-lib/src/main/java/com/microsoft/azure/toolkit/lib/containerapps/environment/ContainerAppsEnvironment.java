/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerapps.environment;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.implementation.ImplUtils;
import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.appcontainers.ContainerAppsApiManager;
import com.azure.resourcemanager.appcontainers.models.BuildConfiguration;
import com.azure.resourcemanager.appcontainers.models.BuildProvisioningState;
import com.azure.resourcemanager.appcontainers.models.BuildResource;
import com.azure.resourcemanager.appcontainers.models.BuildStatus;
import com.azure.resourcemanager.appcontainers.models.BuilderProvisioningState;
import com.azure.resourcemanager.appcontainers.models.BuilderResource;
import com.azure.resourcemanager.appcontainers.models.CheckNameAvailabilityReason;
import com.azure.resourcemanager.appcontainers.models.CheckNameAvailabilityRequest;
import com.azure.resourcemanager.appcontainers.models.CheckNameAvailabilityResponse;
import com.azure.resourcemanager.appcontainers.models.EnvironmentVariable;
import com.azure.resourcemanager.appcontainers.models.ManagedEnvironment;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.action.AzureActionManager;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.exception.StreamingDiagnosticsException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.Availability;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.utils.StreamingLogSupport;
import com.microsoft.azure.toolkit.lib.common.utils.UrlStreamingLog;
import com.microsoft.azure.toolkit.lib.containerapps.AzureContainerApps;
import com.microsoft.azure.toolkit.lib.containerapps.AzureContainerAppsServiceSubscription;
import com.microsoft.azure.toolkit.lib.containerapps.containerapp.ContainerApp;
import com.microsoft.azure.toolkit.lib.containerapps.model.EnvironmentType;
import com.microsoft.azure.toolkit.lib.containerapps.model.WorkloadProfile;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class ContainerAppsEnvironment extends AbstractAzResource<ContainerAppsEnvironment, AzureContainerAppsServiceSubscription, ManagedEnvironment>
    implements Deletable, StreamingLogSupport {
    private static final ObjectMapper mapper = new ObjectMapper();
    public static final Action.Id<ContainerAppsEnvironment> CREATE_CONTAINER_APP = Action.Id.of("user/containerapps.create_container_app");

    protected ContainerAppsEnvironment(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull ContainerAppsEnvironmentModule module) {
        super(name, resourceGroupName, module);
    }

    @Override
    public void refresh() {
        Azure.az(AzureContainerApps.class).containerApps(this.getSubscriptionId()).refresh();
        super.refresh();
    }

    protected ContainerAppsEnvironment(@Nonnull ContainerAppsEnvironment insight) {
        super(insight);
    }

    protected ContainerAppsEnvironment(@Nonnull com.azure.resourcemanager.appcontainers.models.ManagedEnvironment remote, @Nonnull ContainerAppsEnvironmentModule module) {
        super(remote.name(), ResourceId.fromString(remote.id()).resourceGroupName(), module);
    }

    public List<ContainerApp> listContainerApps() {
        return Azure.az(AzureContainerApps.class).containerApps(this.getSubscriptionId()).listContainerAppsByEnvironment(this);
    }

    @Nullable
    public Region getRegion() {
        return Optional.ofNullable(getRemote()).map(remote -> Region.fromName(remote.region().name())).orElse(null);
    }

    @AzureOperation(name = "azure/containerapps.check_name.name", params = "name")
    public Availability checkContainerAppNameAvailability(String name) {
        final CheckNameAvailabilityRequest request = new CheckNameAvailabilityRequest().withName(name).withType("Microsoft.App/containerApps");
        final CheckNameAvailabilityResponse result = Objects.requireNonNull(this.getModule().getParent().getRemote())
            .namespaces().checkNameAvailability(this.getResourceGroupName(), this.getName(), request);
        return new Availability(result.nameAvailable(), Optional.ofNullable(result.reason()).map(CheckNameAvailabilityReason::toString).orElse(null), result.message());
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull ManagedEnvironment remote) {
        return remote.provisioningState().toString();
    }

    @Nullable
    public EnvironmentType getEnvironmentType() {
        return remoteOptional().map(remote -> CollectionUtils.isEmpty(remote.workloadProfiles()) ? EnvironmentType.ConsumptionOnly : EnvironmentType.WorkloadProfiles).orElse(null);
    }

    public List<WorkloadProfile> getWorkloadProfiles() {
        return remoteOptional().map(ManagedEnvironment::workloadProfiles)
            .map(profiles -> profiles.stream().map(WorkloadProfile::fromProfile).collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    }

    public String getLogStreamEndpoint() {
        final ManagedEnvironment remoteEnv = this.getRemote();
        if (Objects.isNull(remoteEnv)) {
            throw new AzureToolkitRuntimeException(AzureString.format("resource ({0}) not found", getName()).toString());
        }
        final String baseUrl = String.format("https://%s.azurecontainerapps.dev", Region.fromName(remoteEnv.location()).getName());
        return String.format("%s/subscriptions/%s/resourceGroups/%s/managedEnvironments/%s/eventstream",
            baseUrl, getSubscriptionId(), getResourceGroupName(), getName());
    }

    @Override
    public String getLogStreamAuthorization() {
        final ContainerAppsApiManager manager = getParent().getRemote();
        final String authToken = Optional.ofNullable(manager).map(m -> m.managedEnvironments().getAuthToken(getResourceGroupName(), getName()).token()).orElse(null);
        return "Bearer " + authToken;
    }

    public BuildResource buildImage(final Path sourceTar, Map<String, String> sourceBuildEnv) {
        final UUID uuid = UUID.randomUUID();
        final String buildId = String.format("build%s", uuid).substring(0, 12);
        final String newBuilderName = String.format("builder%s", uuid).substring(0, 12);

        AzureMessager.getMessager().progress(AzureString.format("Starting the Cloud Build for build of id '%s'", buildId));
        final BuilderResource builder = getOrCreateBuilder(newBuilderName);

        // create a build
        AzureMessager.getMessager().progress(AzureString.format("Creating build '%s' with builder '%s'", buildId, builder.name()));
        final List<EnvironmentVariable> variables = Optional.ofNullable(sourceBuildEnv).orElse(Collections.emptyMap())
            .entrySet().stream().map(e -> new EnvironmentVariable().withName(e.getKey()).withValue(e.getValue()))
            .collect(Collectors.toList());
        final ContainerAppsApiManager manager = Objects.requireNonNull(this.getParent().getRemote());
        final BuildResource build = manager.builds().define(buildId)
            .withExistingBuilder(this.getResourceGroupName(), builder.name())
            .withConfiguration(new BuildConfiguration().withEnvironmentVariables(variables))
            .create();

        // upload source code.
        final String token = getImageBuildAuthToken(build);
        final String uploadEndpoint = build.uploadEndpoint() + "?api-version=" + manager.serviceClient().getApiVersion();
        this.uploadFile(sourceTar, uploadEndpoint, token);
        AzureMessager.getMessager().info("Artifact/compressed source code is uploaded successfully.");
        return build;
    }

    // todo: validate expiration of token and add cache based on build
    @SneakyThrows({MalformedURLException.class, JsonProcessingException.class})
    private String getImageBuildAuthToken(final BuildResource build) {

        final ContainerAppsApiManager manager = Objects.requireNonNull(this.getParent().getRemote());
        final String tokenEndpoint = build.tokenEndpoint() + "?api-version=" + manager.serviceClient().getApiVersion();
        final ImmutableMap<String, Object> body = ImmutableMap.of(
            "location", Optional.ofNullable(this.getRegion()).map(Region::getName).orElse(com.azure.core.management.Region.US_EAST.name()),
            "properties", Collections.emptyMap()
        );
        AzureMessager.getMessager().progress(AzureString.format("Loading token for uploading artifact/compressed source code."));
        final HttpRequest tokenRequest = new HttpRequest(HttpMethod.POST, ImplUtils.createUrl(tokenEndpoint), new HttpHeaders(), BinaryData.fromObject(body));
        final HttpPipeline pipeline = manager.serviceClient().getHttpPipeline();
        try (final HttpResponse tokenResponse = pipeline.send(tokenRequest).block()) {
            if (Objects.nonNull(tokenResponse) && tokenResponse.getStatusCode() == 200) {
                final String responseBodyString = tokenResponse.getBodyAsString().block();
                return mapper.readTree(responseBodyString).get("token").asText();
            }
        }
        throw new AzureToolkitRuntimeException("Failed to get token for image build.");
    }

    @Nullable
    public String waitForImageBuilding(final BuildResource build) {
        final ImmutableSet<BuildProvisioningState> errorProvisioningStates = ImmutableSet.of(BuildProvisioningState.CANCELED, BuildProvisioningState.FAILED, BuildProvisioningState.DELETING);
        final ImmutableSet<BuildProvisioningState> waitingProvisioningStates = ImmutableSet.of(BuildProvisioningState.CREATING, BuildProvisioningState.UPDATING);

        final UrlStreamingLog urlStreamingLog = UrlStreamingLog.builder()
            .authorization("Bearer " + getImageBuildAuthToken(build)).endpoint(build.logStreamEndpoint()).name(build.name()).build();
        final Action<StreamingLogSupport> viewLogInToolkit = AzureActionManager.getInstance().getAction(StreamingLogSupport.OPEN_STREAMING_LOG)
            .bind(urlStreamingLog).withLabel("Open streaming logs");
        AzureMessager.getMessager().info(AzureString.format("Waiting for the build %s to be provisioned...", build.name()), viewLogInToolkit);
        BuildProvisioningState provisioningState = build.provisioningState();
        while (waitingProvisioningStates.contains(provisioningState)) {
            ResourceManagerUtils.sleep(Duration.ofSeconds(3));
            build.refresh();
            provisioningState = build.provisioningState();
        }
        if (errorProvisioningStates.contains(provisioningState)) {
            throw new AzureToolkitRuntimeException(String.format("The build %s is not provisioned properly, status: %s", build.name(), provisioningState));
        }
        AzureMessager.getMessager().info(AzureString.format("Build %s is provisioned successfully", build.name()));

        final ImmutableSet<BuildStatus> errorBuildingStates = ImmutableSet.of(BuildStatus.FAILED, BuildStatus.CANCELED);
        final ImmutableSet<BuildStatus> waitingBuildingStates = ImmutableSet.of(BuildStatus.NOT_STARTED, BuildStatus.IN_PROGRESS);
        AzureMessager.getMessager().progress(AzureString.format("Waiting for the build %s to be completed...", build.name()));
        BuildStatus buildStatus = build.buildStatus();
        while (waitingBuildingStates.contains(buildStatus)) {
            ResourceManagerUtils.sleep(Duration.ofSeconds(3));
            build.refresh();
            buildStatus = build.buildStatus();
        }
        if (errorBuildingStates.contains(buildStatus)) {
            final String message = String.format("The build %s is provisioned properly but its build status is %s", build.name(), buildStatus);
            throw new StreamingDiagnosticsException(message, urlStreamingLog);
        }
        final String image = build.destinationContainerRegistry().image();
        AzureMessager.getMessager().info(AzureString.format("Build %s is completed successfully, image %s is built.", build.name(), image));
        return image;
    }

    public BuilderResource getOrCreateBuilder(final String builderName) {
        final ContainerAppsApiManager manager = Objects.requireNonNull(this.getParent().getRemote());
        final String environmentName = this.getName();
        final List<BuilderResource> builders = manager.builders().listByResourceGroup(this.getResourceGroupName()).stream()
            .filter(b -> b.environmentId().endsWith(String.format("/%s", environmentName)))
            .collect(Collectors.toList());
        if (!builders.isEmpty()) {
            final BuilderResource builder = builders.get(0);
            if (builder.provisioningState() != BuilderProvisioningState.SUCCEEDED) {
                throw new AzureToolkitRuntimeException(String.format("Selected builder %s is not ready to build (current status: %s).", builder.name(), builder.provisioningState()));
            }
            AzureMessager.getMessager().info(AzureString.format("Use existing builder %s in environment %s", builderName, this.getName()));
            return builder;
        }
        AzureMessager.getMessager().progress(AzureString.format("Creating new builder %s in environment %s", builderName, this.getName()));
        return manager.builders().define(builderName)
            .withRegion(Optional.ofNullable(this.getRegion()).map(Region::getName).orElse(com.azure.core.management.Region.US_EAST.name()))
            .withExistingResourceGroup(this.getResourceGroupName())
            .withEnvironmentId(this.getId())
            .create();
    }

    public void uploadFile(Path tarFile, String uploadEndpoint, String token) {
        final File dataFile = tarFile.toFile();
        final String fileName = dataFile.getName();

        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final HttpPost request = new HttpPost(uploadEndpoint);
            request.addHeader("Authorization", "Bearer " + token);
            final FileBody fileBody = new FileBody(tarFile.toFile());
            final HttpEntity multipartEntity = MultipartEntityBuilder.create().addPart("file", fileBody).build();
            request.setEntity(multipartEntity);
            final CloseableHttpResponse response = httpClient.execute(request);
            final int code = response.getStatusLine().getStatusCode();
            if (code != 200) {
                final HttpEntity responseEntity = response.getEntity();
                if (responseEntity != null) {
                    final String responseString = EntityUtils.toString(responseEntity);
                    throw new AzureToolkitRuntimeException(String.format("Error when uploading artifact/source code, request exited with %s: %s", code, responseString));
                }
                throw new AzureToolkitRuntimeException(String.format("Error when uploading artifact/source code, request exited with %s", code));
            }
        } catch (final Exception e) {
            throw new AzureToolkitRuntimeException("Error when uploading artifact/source code", e);
        }
    }
}
