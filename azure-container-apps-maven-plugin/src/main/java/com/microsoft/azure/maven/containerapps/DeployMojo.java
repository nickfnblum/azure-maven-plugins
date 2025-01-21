/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.maven.containerapps;

import com.fasterxml.jackson.dataformat.javaprop.JavaPropsMapper;
import com.fasterxml.jackson.dataformat.javaprop.JavaPropsSchema;
import com.microsoft.azure.maven.utils.MavenConfigUtils;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.containerapps.config.ContainerAppConfig;
import com.microsoft.azure.toolkit.lib.containerapps.task.DeployContainerAppTask;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.IOException;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Deploy your project to target Azure Container app. If target app doesn't exist, it will be created.
 */
@Mojo(name = "deploy")
@Slf4j
public class DeployMojo extends AbstractMojoBase {
    @Override
    @AzureOperation("user/containerapps.deploy_mojo")
    protected void doExecute() throws Throwable {
        this.mergeCommandLineConfig();
        loginAzure();
        selectSubscription();
        final ContainerAppConfig config = this.getConfiguration();
        final DeployContainerAppTask task = new DeployContainerAppTask(config);
        task.execute();
    }

    private void mergeCommandLineConfig() {
        try {
            final JavaPropsMapper mapper = new JavaPropsMapper();
            mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
            final DeployMojo commandLineConfig = mapper.readSystemPropertiesAs(JavaPropsSchema.emptySchema(), DeployMojo.class);
            com.microsoft.azure.toolkit.lib.common.utils.Utils.copyProperties(this, commandLineConfig, false);
        } catch (IOException | IllegalAccessException e) {
            throw new AzureToolkitRuntimeException("failed to merge command line configuration", e);
        }
    }

    protected boolean checkProjectPackaging(MavenProject project) throws MojoExecutionException {
        if (MavenConfigUtils.isJarPackaging(project)) {
            return true;
        } else if (MavenConfigUtils.isPomPackaging(project)) {
            log.info("Packaging type is pom, taking no actions.");
            return false;
        } else {
            throw new MojoExecutionException(String.format("`azure-container-apps:deploy` does not support maven project with " +
                "packaging %s, only jar is supported", project.getPackaging()));
        }
    }

    protected boolean checkConfiguration() {
        final String pluginKey = plugin.getPluginLookupKey();
        final Xpp3Dom pluginDom = MavenConfigUtils.getPluginConfiguration(project, pluginKey);
        if (pluginDom == null || pluginDom.getChildren().length == 0) {
            log.warn("Configuration does not exist, taking no actions.");
            return false;
        } else {
            return true;
        }
    }

    @SneakyThrows
    @Override
    protected boolean isSkipMojo() {
        return !checkProjectPackaging(project) || !checkConfiguration();
    }
}
