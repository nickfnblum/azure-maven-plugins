/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.storage.blob;

import com.azure.core.util.paging.ContinuablePage;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzServiceSubscription;
import com.microsoft.azure.toolkit.lib.common.model.AbstractEmulatableAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.page.ItemPage;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.storage.IStorageAccount;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

public class BlobContainerModule extends AbstractEmulatableAzResourceModule<BlobContainer, IStorageAccount, BlobContainerClient> {

    public static final String NAME = "Azure.BlobContainer";
    private BlobServiceClient client;

    public BlobContainerModule(@Nonnull IStorageAccount parent) {
        super(NAME, parent);
    }

    @Override
    protected void invalidateCache() {
        super.invalidateCache();
        this.client = null;
    }

    @Nullable
    synchronized BlobServiceClient getBlobServiceClient() {
        if (Objects.isNull(this.client) && this.parent.exists()) {
            final String connectionString = this.parent.getConnectionString();
            this.client = new BlobServiceClientBuilder().addPolicy(AbstractAzServiceSubscription.getUserAgentPolicy()).connectionString(connectionString).buildClient();
        }
        return this.client;
    }

    @Nonnull
    @Override
    protected Iterator<? extends ContinuablePage<String, BlobContainerClient>> loadResourcePagesFromAzure() {
        if (!this.parent.exists()) {
            return Collections.emptyIterator();
        }
        final BlobServiceClient client = this.getBlobServiceClient();
        return Objects.requireNonNull(client).listBlobContainers().streamByPage(getPageSize())
            .map(p -> new ItemPage<>(p.getValue().stream().map(c -> client.getBlobContainerClient(c.getName()))))
            .iterator();
    }

    @Nullable
    @Override
    protected BlobContainerClient loadResourceFromAzure(@Nonnull String name, @Nullable String resourceGroup) {
        if (!this.parent.exists()) {
            return null;
        }
        final BlobServiceClient client = this.getBlobServiceClient();
        return Objects.requireNonNull(client).listBlobContainers().stream()
            .map(s -> client.getBlobContainerClient(s.getName()))
            .filter(c -> c.getBlobContainerName().equals(name))
            .findAny().orElse(null);
    }

    @Override
    @AzureOperation(name = "azure/storage.delete_blob_container.container", params = {"nameFromResourceId(resourceId)"})
    protected void deleteResourceFromAzure(@Nonnull String resourceId) {
        final ResourceId id = ResourceId.fromString(resourceId);
        final BlobServiceClient client = this.getBlobServiceClient();
        Objects.requireNonNull(client).deleteBlobContainer(id.name());
    }

    @Nonnull
    @Override
    protected BlobContainerDraft newDraftForCreate(@Nonnull String name, @Nullable String resourceGroupName) {
        return new BlobContainerDraft(name, this);
    }

    @Nonnull
    protected BlobContainer newResource(@Nonnull BlobContainerClient r) {
        return new BlobContainer(r.getBlobContainerName(), this);
    }

    @Nonnull
    protected BlobContainer newResource(@Nonnull String name, @Nullable String resourceGroupName) {
        return new BlobContainer(name, this);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Blob Container";
    }
}
