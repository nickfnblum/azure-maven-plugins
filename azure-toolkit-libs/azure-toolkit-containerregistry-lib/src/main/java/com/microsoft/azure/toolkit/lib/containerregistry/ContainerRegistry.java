/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerregistry;

import com.azure.core.util.BinaryData;
import com.azure.resourcemanager.containerregistry.ContainerRegistryManager;
import com.azure.resourcemanager.containerregistry.fluent.models.RegistryInner;
import com.azure.resourcemanager.containerregistry.models.AccessKeyType;
import com.azure.resourcemanager.containerregistry.models.ImageDescriptor;
import com.azure.resourcemanager.containerregistry.models.OverridingArgument;
import com.azure.resourcemanager.containerregistry.models.ProvisioningState;
import com.azure.resourcemanager.containerregistry.models.PublicNetworkAccess;
import com.azure.resourcemanager.containerregistry.models.Registry;
import com.azure.resourcemanager.containerregistry.models.RegistryTaskRun;
import com.azure.resourcemanager.containerregistry.models.RunStatus;
import com.azure.resourcemanager.containerregistry.models.SourceUploadDefinition;
import com.azure.resourcemanager.resources.fluentcore.utils.ResourceManagerUtils;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.blob.specialized.SpecializedBlobClientBuilder;
import com.google.common.collect.ImmutableSet;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.action.AzureActionManager;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.exception.StreamingDiagnosticsException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.utils.StreamingLogSupport;
import com.microsoft.azure.toolkit.lib.containerregistry.model.Sku;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ContainerRegistry extends AbstractAzResource<ContainerRegistry, AzureContainerRegistryServiceSubscription, Registry> {
    public static final String ACR_IMAGE_SUFFIX = ".azurecr.io";
    @Getter
    private final RepositoryModule repositoryModule;

    protected ContainerRegistry(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull AzureContainerRegistryModule module) {
        super(name, resourceGroupName, module);
        this.repositoryModule = new RepositoryModule(this);
    }

    protected ContainerRegistry(@Nonnull ContainerRegistry registry) {
        super(registry);
        this.repositoryModule = registry.repositoryModule;
    }

    protected ContainerRegistry(@Nonnull Registry registry, @Nonnull AzureContainerRegistryModule module) {
        super(registry.name(), registry.resourceGroupName(), module);
        this.repositoryModule = new RepositoryModule(this);
    }

    @Nonnull
    @Override
    public List<AbstractAzResourceModule<?, ?, ?>> getSubModules() {
        return Collections.singletonList(this.repositoryModule);
    }

    @Nonnull
    @Override
    protected String loadStatus(@Nonnull Registry remote) {
        return Optional.ofNullable(remote.innerModel()).map(RegistryInner::provisioningState).map(ProvisioningState::toString).orElse(Status.UNKNOWN);
    }

    public boolean isAdminUserEnabled() {
        return remoteOptional().map(Registry::adminUserEnabled).orElse(false);
    }

    public boolean isPublicAccessEnabled() {
        return remoteOptional().map(r -> r.publicNetworkAccess() == PublicNetworkAccess.ENABLED).orElse(true);
    }

    @AzureOperation(name = "internal/acr.enable_admin_user.registry", params = "this.getName()")
    public void enableAdminUser() {
        ContainerRegistryDraft update = (ContainerRegistryDraft) this.update();
        update.setAdminUserEnabled(true);
        update.commit();
    }

    @AzureOperation(name = "internal/acr.disable_admin_user.registry", params = "this.getName()")
    public void disableAdminUser() {
        ContainerRegistryDraft update = (ContainerRegistryDraft) this.update();
        update.setAdminUserEnabled(false);
        update.commit();
    }

    @Nullable
    public Sku getSku() {
        return remoteOptional().map(Registry::sku).map(sku -> sku.tier().toString()).map(Sku::valueOf).orElse(null);
    }

    @Nullable
    public Region getRegion() {
        return remoteOptional().map(registry -> registry.region().name()).map(Region::fromName).orElse(null);
    }

    @Nullable
    public String getUserName() {
        return remoteOptional().map(registry -> registry.getCredentials().username()).orElse(null);
    }

    @Nullable
    public String getPrimaryCredential() {
        return remoteOptional().map(registry -> registry.getCredentials().accessKeys())
            .map(map -> map.get(AccessKeyType.PRIMARY)).orElse(null);
    }

    @Nullable
    public String getSecondaryCredential() {
        return remoteOptional().map(registry -> registry.getCredentials().accessKeys())
            .map(map -> map.get(AccessKeyType.SECONDARY)).orElse(null);
    }

    @Nullable
    public String getLoginServerUrl() {
        return remoteOptional().map(Registry::loginServerUrl).orElse(null);
    }

    @Nullable
    public String getType() {
        return remoteOptional().map(Registry::type).orElse(null);
    }

    /**
     * @return image build task run, null if registry is not ready
     */
    @Nullable
    public RegistryTaskRun buildImage(final String imageNameWithTag, final Path sourceTar) {
        return buildImage(imageNameWithTag, sourceTar, "./Dockerfile", Collections.emptyMap());
    }

    @Nullable
    public RegistryTaskRun buildImage(final String imageNameWithTag, final Path sourceTar, final String dockerFilePath, final Map<String, OverridingArgument> buildArguments) {
        return this.remoteOptional().map(r -> {
            // upload tar.gz file
            AzureMessager.getMessager().progress(AzureString.format("Uploading compressed source code to Registry '%s'.", this.getName()));
            final SourceUploadDefinition upload = r.getBuildSourceUploadUrl();
            final BlockBlobClient blobClient = new SpecializedBlobClientBuilder().endpoint(upload.uploadUrl()).buildBlockBlobClient();
            blobClient.upload(BinaryData.fromFile(sourceTar));

            AzureMessager.getMessager().progress(AzureString.format("Building image '%s' in Registry '%s'.", imageNameWithTag, this.getName()));
            return r.scheduleRun().withLinux().withDockerTaskRunRequest()
                .defineDockerTaskStep()
                .withDockerFilePath(dockerFilePath)
                .withOverridingArguments(buildArguments)
                .withImageNames(Collections.singletonList(imageNameWithTag))
                .withPushEnabled(true)
                .attach()
                .withSourceLocation(upload.relativePath())
                .execute();
        }).orElse(null);
    }

    @Nullable
    public String waitForImageBuilding(@Nonnull final RegistryTaskRun run) {
        final ImmutableSet<RunStatus> errorStatus = ImmutableSet.of(RunStatus.FAILED, RunStatus.CANCELED, RunStatus.ERROR, RunStatus.TIMEOUT);
        final ImmutableSet<RunStatus> waitingStatus = ImmutableSet.of(RunStatus.QUEUED, RunStatus.STARTED, RunStatus.RUNNING);

        final ContainerRegistryManager registryManager = Objects.requireNonNull(this.getParent().getRemote());
        String logSasUrl = registryManager.registryTaskRuns().getLogSasUrl(this.getResourceGroupName(), this.getName(), run.runId());
        if (!logSasUrl.startsWith("https://") && !logSasUrl.startsWith("http://")) {
            logSasUrl = "https://" + logSasUrl;
        }
        final Action<String> openUrl = AzureActionManager.getInstance().getAction(Action.OPEN_URL);
        final Action<String> viewLogInBrowser = openUrl.bind(logSasUrl).withLabel("Open streaming logs in browser");
        final RegistryTaskRunStreamingLog urlStreamingLog = RegistryTaskRunStreamingLog.builder().logSasUrl(logSasUrl).task(run).build();
        final Action<StreamingLogSupport> viewLogInToolkit = Optional.ofNullable(AzureActionManager.getInstance().getAction(StreamingLogSupport.OPEN_STREAMING_LOG))
            .map(action -> action.bind(urlStreamingLog).withLabel("Open streaming logs"))
            .orElse(null);
        AzureMessager.getMessager().info(AzureString.format("Waiting for image building task run (%s) to be completed...", run.runId()), viewLogInToolkit, viewLogInBrowser);
        RunStatus status = run.status();
        while (waitingStatus.contains(status)) {
            ResourceManagerUtils.sleep(Duration.ofSeconds(10));
            run.refresh();
            status = run.status();
        }
        final List<ImageDescriptor> images = run.innerModel().outputImages();
        if (errorStatus.contains(status) || CollectionUtils.isEmpty(images)) {
            final String message = String.format("Failed to build image (status: %s). View logs at %s for more details.", status, logSasUrl);
            throw new StreamingDiagnosticsException(message, urlStreamingLog);
        }
        final ImageDescriptor image = images.get(0);
        final String fullImageName = String.format("%s/%s:%s", image.registry(), image.repository(), image.tag());
        AzureMessager.getMessager().info(AzureString.format("Image building task run %s is completed successfully, image %s is built.", run.runId(), fullImageName), viewLogInToolkit, viewLogInBrowser);
        // refresh to load newly build images.
        this.refresh();
        ResourceManagerUtils.sleep(Duration.ofSeconds(3));
        return fullImageName;
    }
}
