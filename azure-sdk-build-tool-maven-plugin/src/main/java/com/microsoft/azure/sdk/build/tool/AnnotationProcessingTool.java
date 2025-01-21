// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.sdk.build.tool;

import com.microsoft.azure.sdk.build.tool.models.BuildErrorCode;
import com.microsoft.azure.sdk.build.tool.models.MethodCallDetails;
import com.microsoft.azure.sdk.build.tool.mojo.AzureSdkMojo;
import com.microsoft.azure.sdk.build.tool.util.AnnotatedMethodCallerResult;
import com.microsoft.azure.sdk.build.tool.util.logging.Logger;
import com.microsoft.azure.sdk.build.tool.util.AnnotationUtils;
import com.microsoft.azure.sdk.build.tool.util.MojoUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs the following tasks:
 *
 * <ul>
 *   <li>Reporting to the user all use of @ServiceMethods.</li>
 *   <li>Reporting to the user all use of @Beta-annotated APIs.</li>
 * </ul>
 */
public class AnnotationProcessingTool implements Runnable {
    private static final Logger LOGGER = Logger.getInstance();

    /**
     * Runs the annotation processing task to look for @ServiceMethod and @Beta usage.
     */
    public void run() {
        LOGGER.info("Running Annotation Processing Tool");

        // We build up a list of packages in the source of the user maven project, so that we only report on the
        // usage of annotation methods from code within these packages
        final Set<String> interestedPackages = new TreeSet<>(Comparator.comparingInt(String::length));
        MojoUtils.getCompileSourceRoots().forEach(root -> buildPackageList(root, root, interestedPackages));

        final List<Path> allPaths = getAllPaths();
        final ClassLoader classLoader = AnnotationUtils.getCompleteClassLoader(allPaths.stream());

        // Collect all calls to methods annotated with the Azure SDK @ServiceMethod annotation
        Optional<Set<AnnotatedMethodCallerResult>> serviceMethodCallers = AnnotationUtils.getAnnotation("com.azure.core.annotation.ServiceMethod", classLoader)
            .map(a -> AnnotationUtils.findCallsToAnnotatedMethod(a, allPaths.stream(), interestedPackages, true));

        if (serviceMethodCallers.isPresent()) {
            List<MethodCallDetails> serviceMethodCallDetails = getMethodCallDetails(serviceMethodCallers.get());
            AzureSdkMojo.getMojo().getReport().setServiceMethodCalls(serviceMethodCallDetails);
        }

        // Collect all calls to methods annotated with the Azure SDK @Beta annotation
        Optional<Set<AnnotatedMethodCallerResult>> betaMethodCallers = AnnotationUtils.getAnnotation("com.azure.cosmos.util.Beta", classLoader)
            .map(a -> AnnotationUtils.findCallsToAnnotatedMethod(a, allPaths.stream(), interestedPackages, true));
        if (betaMethodCallers.isPresent()) {
            List<MethodCallDetails> betaMethodCallDetails = getMethodCallDetails(betaMethodCallers.get());

            AzureSdkMojo.getMojo().getReport().setBetaMethodCalls(betaMethodCallDetails);
            if (!betaMethodCallers.get().isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append(MojoUtils.getString("betaApiUsed")).append(System.lineSeparator());
                betaMethodCallers.get().forEach(method -> message.append("   - ").append(method.toString()).append(System.lineSeparator()));
                MojoUtils.failOrWarn(() -> AzureSdkMojo.getMojo().isValidateNoBetaApiUsed(), BuildErrorCode.BETA_API_USED, message.toString());
            }
        }
    }

    private List<MethodCallDetails> getMethodCallDetails(Set<AnnotatedMethodCallerResult> betaMethodCallers) {
        return betaMethodCallers.stream()
            .map(AnnotatedMethodCallerResult::getAnnotatedMethod)
            .map(Method::toGenericString)
            .sorted()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(e -> 1)))
            .entrySet()
            .stream()
            .map(entry -> new MethodCallDetails().setMethodName(entry.getKey()).setCallFrequency(entry.getValue()))
            .collect(Collectors.toList());
    }

    private static List<Path> getAllPaths() {
        // This is the user maven build target directory - we look in here for the compiled source code
        final File targetDir = new File(AzureSdkMojo.getMojo().getProject().getBuild().getDirectory() + "/classes/");

        // this is a list containing the users maven project compiled class files, as well as all
        // jar file dependencies. We use this to analyse the use of annotations and report back to the user.
        List<Path> allPaths = new ArrayList<>();
        allPaths.add(Paths.get(targetDir.getAbsolutePath()));
        final List<Path> collect = MojoUtils.getAllDependencies().stream().map(a -> a.getFile().getAbsolutePath()).map(Paths::get).collect(Collectors.toList());
        allPaths.addAll(collect);
        return allPaths;
    }

    private static void buildPackageList(String rootDir, String currentDir, Set<String> packages) {
        final File directory = new File(currentDir);

        final File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (final File file : files) {
            if (file.isFile()) {
                final String path = file.getPath();
                final String packageName = path.substring(rootDir.length() + 1, path.lastIndexOf(File.separator));
                packages.add(packageName.replace(File.separatorChar, '.'));
            } else if (file.isDirectory()) {
                buildPackageList(rootDir, file.getAbsolutePath(), packages);
            }
        }
    }
}
