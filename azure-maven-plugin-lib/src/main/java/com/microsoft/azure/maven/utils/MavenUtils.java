/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.maven.utils;

import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.stream.Stream;

public class MavenUtils {

    public static Xpp3Dom getPluginConfiguration(MavenProject mavenProject, String pluginKey) {
        final Plugin plugin = getPluginFromMavenModel(mavenProject.getModel(), pluginKey);
        return plugin == null ? null : (Xpp3Dom) plugin.getConfiguration();
    }

    public static String getJavaVersion(MavenProject mavenProject) {
        return Stream.of("java.version", "maven.compiler.source", "maven.compiler.release")
            .map(key -> mavenProject.getProperties().getProperty(key))
            .filter(StringUtils::isNotEmpty)
            .findFirst()
            .map(Utils::getJavaMajorVersion)
            .map(String::valueOf)
            .orElse("");
    }

    public static Boolean isSpringBootProject(MavenProject mavenProject) {
        return getPluginConfiguration(mavenProject, "org.springframework.boot:spring-boot-maven-plugin") != null;
    }

    private static Plugin getPluginFromMavenModel(Model model, String pluginKey) {
        if (model.getBuild() == null) {
            return null;
        }
        for (final Plugin plugin: model.getBuild().getPlugins()) {
            if (pluginKey.equalsIgnoreCase(plugin.getKey())) {
                return plugin;
            }
        }

        if (model.getBuild().getPluginManagement() == null) {
            return null;
        }

        for (final Plugin plugin: model.getBuild().getPluginManagement().getPlugins()) {
            if (pluginKey.equalsIgnoreCase(plugin.getKey())) {
                return plugin;
            }
        }

        return null;
    }

    private MavenUtils() {

    }

}
