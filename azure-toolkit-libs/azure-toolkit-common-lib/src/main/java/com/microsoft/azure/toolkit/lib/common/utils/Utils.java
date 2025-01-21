/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.utils;

import com.azure.resourcemanager.resources.fluentcore.utils.ResourceNamer;
import com.google.common.base.Preconditions;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.exception.CommandExecuteException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {
    private static final boolean isWindows = System.getProperty("os.name").contains("Windows");
    private static final String POM = "pom";
    private static final String JAR = "jar";
    private static final String WAR = "war";
    private static final String EAR = "ear";
    private static final String SUBSCRIPTIONS = "subscriptions";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMddHHmmss");
    private static final String MAIN_CLASS = "Main-Class";
    private static final String CLASS = ".class";
    private static final String SPRING_BOOT_CLASSES = "Spring-Boot-Classes";
    private static final String START_CLASS = "Start-Class";
    private static final String DEFAULT_SPRING_BOOT_CLASSES = "BOOT-INF/classes/";
    public static final int DEFAULT_TIMEOUT = 10000;

    public static String generateRandomResourceName(@Nonnull final String prefix, final int maxLength) {
        final String name = String.format("%s-%s", prefix, Utils.getTimestamp());
        return name.length() <= maxLength ? name : new ResourceNamer(StringUtils.EMPTY).getRandomName(name.substring(0, maxLength - 10), maxLength);
    }

    public static String getTimestamp() {
        return DATE_FORMAT.format(new Date());
    }

    public static int getJavaMajorVersion(final String javaVersion) {
        final String runtimeJavaMajorVersion = StringUtils.startsWith(javaVersion, "1.") ?
            StringUtils.substring(javaVersion, 2, 3) : StringUtils.split(javaVersion, ".")[0];
        return Integer.parseInt(runtimeJavaMajorVersion);
    }

    /**
     * Get artifact compile version based on class file
     * For spring artifact, will check compile level of Start-Class, for others will check Main-Class.
     * If none of above exists, will check compile level of first class in artifact
     *
     * @throws AzureToolkitRuntimeException If there is no class file in target artifact or meet IOException when read target artifact
     */
    public static int getArtifactCompileVersion(@Nonnull final File artifact) throws AzureToolkitRuntimeException {
        try (final JarFile jarFile = new JarFile(artifact)) {
            final Manifest manifest = jarFile.getManifest();
            final JarEntry userEntry = getUserEntry(jarFile, manifest);
            final JarEntry springStartEntry = getSpringStartEntry(jarFile, manifest);
            return Stream.of(userEntry, springStartEntry).filter(Objects::nonNull).mapToInt(entry -> getJarEntryJavaVersion(jarFile, entry)).max()
                .orElseThrow(() -> new AzureToolkitRuntimeException("Failed to parse artifact compile version, no valid class file founded in target artifact"));
        } catch (final IOException e) {
            throw new AzureToolkitRuntimeException("Failed to parse artifact compile version, no class file founded in target artifact", e);
        }
    }

    @Nullable
    private static JarEntry getSpringStartEntry(@Nonnull final JarFile jarFile, @Nonnull final Manifest manifest) {
        final String startClass = manifest.getMainAttributes().getValue(START_CLASS);
        final String springBootClasses = Optional.ofNullable(manifest.getMainAttributes().getValue(SPRING_BOOT_CLASSES)).orElse(DEFAULT_SPRING_BOOT_CLASSES);
        return jarFile.getJarEntry(getJarEntryName(springBootClasses + startClass));
    }

    @Nullable
    private static JarEntry getUserEntry(@Nonnull final JarFile jarFile, @Nonnull final Manifest manifest) {
        return Optional.ofNullable(manifest.getMainAttributes().getValue(MAIN_CLASS)).map(Utils::getJarEntryName).map(jarFile::getJarEntry)
            .orElseGet(() -> jarFile.stream().filter(entry -> StringUtils.endsWith(entry.getName(), CLASS)).findFirst().orElse(null));
    }

    private static int getJarEntryJavaVersion(@Nonnull final JarFile jarFile, @Nonnull final JarEntry jarEntry) {
        // Read compile version from class file
        // Refers https://en.wikipedia.org/wiki/Java_class_file#General_layout
        try (final InputStream stream = jarFile.getInputStream(jarEntry)) {
            final byte[] version = new byte[2];
            stream.skip(6);
            stream.read(version);
            stream.close();
            return new BigInteger(version).intValueExact() - 44;
        } catch (final IOException e) {
            throw new AzureToolkitRuntimeException(String.format("Failed to parse compile version of entry %s", jarEntry.getName()), e);
        }
    }

    @Nonnull
    private static String getJarEntryName(@Nonnull final String className) {
        final String fullName = StringUtils.replace(className, ".", "/");
        return fullName + ".class";
    }

    public static boolean isGUID(String input) {
        try {
            return UUID.fromString(input).toString().equalsIgnoreCase(input);
        } catch (final Exception e) {
            return false;
        }
    }

    // Copied from https://github.com/microsoft/azure-tools-for-java/blob/azure-intellij-toolkit-v3.39.0/Utils/
    // azuretools-core/src/com/microsoft/azuretools/core/mvp/model/AzureMvpModel.java
    // Todo: Remove duplicated utils function in azure-tools-for-java
    @Nullable
    public static String getSegment(String id, String segment) {
        if (StringUtils.isEmpty(id)) {
            return null;
        }
        final String[] attributes = StringUtils.lowerCase(id).split("/");
        final int pos = ArrayUtils.indexOf(attributes, StringUtils.lowerCase(segment));
        if (pos >= 0) {
            return attributes[pos + 1];
        }
        return null;
    }

    public static String getSubscriptionId(String resourceId) {
        return getSegment(resourceId, SUBSCRIPTIONS);
    }

    public static boolean isPomPackagingProject(String packaging) {
        return POM.equalsIgnoreCase(packaging);
    }

    public static boolean isJarPackagingProject(String packaging) {
        return JAR.equalsIgnoreCase(packaging);
    }

    public static boolean isWarPackagingProject(String packaging) {
        return WAR.equalsIgnoreCase(packaging);
    }

    public static boolean isEarPackagingProject(String packaging) {
        return EAR.equalsIgnoreCase(packaging);
    }

    public static String calcXmlIndent(String[] lines, int row, int column) {
        Preconditions.checkNotNull(lines, "The parameter 'lines' cannot be null");
        Preconditions.checkArgument(lines.length > row && row >= 0, "The parameter 'row' overflows.");
        final String line = lines[row];
        Preconditions.checkArgument(line != null, "Encounter null on row: " + row);
        Preconditions.checkArgument(line.length() >= column && column >= 0, "The parameter 'column' overflows");

        final StringBuilder buffer = new StringBuilder();
        final int pos = line.lastIndexOf('<', column) - 1; // skip the current tag like : <tag>
        for (int i = 0; i <= pos; i++) {
            if (line.charAt(i) == '\t') {
                buffer.append('\t');
            } else {
                buffer.append(' ');
            }
        }
        return buffer.toString();
    }

    public static String executeCommandAndGetOutput(final String cmd, File cwd)
        throws IOException, InterruptedException {
        final String[] cmds = new String[]{isWindows ? "cmd.exe" : "bash", isWindows ? "/c" : "-c", cmd};
        final Process p = Runtime.getRuntime().exec(cmds, null, cwd);
        final int exitCode = p.waitFor();
        if (exitCode != 0) {
            final String errorLog = IOUtils.toString(p.getErrorStream(), StandardCharsets.UTF_8);
            throw new CommandExecuteException(String.format("Cannot execute '%s' due to error: %s", cmd, errorLog));
        }
        return IOUtils.toString(p.getInputStream(), StandardCharsets.UTF_8);
    }

    public static Collection<String> intersectIgnoreCase(List<String> list1, List<String> list2) {
        if (CollectionUtils.isNotEmpty(list1) && CollectionUtils.isNotEmpty(list2)) {
            return list2.stream().filter(str -> containsIgnoreCase(list1, str)).collect(Collectors.toSet());
        }
        return Collections.emptyList();
    }

    public static boolean containsIgnoreCase(List<String> list, String str) {
        if (StringUtils.isNotBlank(str) && CollectionUtils.isNotEmpty(list)) {
            return list.stream().anyMatch(str2 -> StringUtils.equalsIgnoreCase(str, str2));
        }
        return false;
    }

    public static String getId(Object obj) {
        return Integer.toHexString(System.identityHashCode(obj));
    }

    public static <K, V> Map<K, V> groupByIgnoreDuplicate(Collection<V> list, Function<? super V, ? extends K> keyMapper) {
        return list.stream().collect(Collectors.toMap(keyMapper, item -> item, (item1, item2) -> item1));
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        final Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static <T> T selectFirstOptionIfCurrentInvalid(String name, List<T> options, T value) {
        if (options.isEmpty()) {
            throw new AzureToolkitRuntimeException(String.format("No %s is available.", name));
        }
        return options.contains(value) ? value : options.get(0);
    }

    public static <T> void copyProperties(T to, T from, boolean whenNotSet) throws IllegalAccessException {
        for (final Field field : FieldUtils.getAllFields(from.getClass())) {
            if (Modifier.isFinal(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            final Object fromValue = FieldUtils.readField(field, from, true);
            final Object toValue = FieldUtils.readField(field, to, true);
            final Class<?> type = field.getType();
            final boolean isCustomObject = !(type.getName().startsWith("java") || type.isPrimitive() || type.isEnum() || type.isAssignableFrom(String.class) || type.isArray());
            if (isCustomObject && ObjectUtils.allNotNull(fromValue, toValue)) {
                copyProperties(toValue, fromValue, whenNotSet);
            } else {
                if ((!whenNotSet || toValue == null) && fromValue != null) {
                    FieldUtils.writeField(field, to, fromValue, true);
                }
            }
        }
    }

    @Nullable
    public static <T> T emptyToNull(T t) {
        if (t instanceof Map && MapUtils.isEmpty((Map<?, ?>) t) ||
            t instanceof CharSequence && StringUtils.isBlank((CharSequence) t) ||
            t instanceof Collection && CollectionUtils.isEmpty((Collection<?>) t)) {
            return null;
        }
        return t;
    }

    public static boolean isUrlAccessible(@Nonnull final String url, @Nonnull final Integer... validResponseCodes) {
        HttpURLConnection.setFollowRedirects(false);
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");
            con.setReadTimeout(DEFAULT_TIMEOUT);
            return ArrayUtils.contains(validResponseCodes, con.getResponseCode());
        } catch (final IOException ex) {
            return false;
        } finally {
            Optional.ofNullable(con).ifPresent(HttpURLConnection::disconnect);
        }
    }

    public static boolean containsIgnoreCases(@Nonnull final String target, @Nonnull List<String> list) {
        return list.stream().anyMatch(item -> StringUtils.equalsIgnoreCase(target, item));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T get(Map<String, Object> data, String path) {
        if (!path.startsWith("$.")) {
            throw new AzureToolkitRuntimeException("invalid json path");
        }
        final String[] parts = path.substring(2).split("\\.");
        Object obj = data;
        for (final String p : parts) {
            if (Objects.isNull(obj)) {
                return null;
            }
            final Map<?, ?> d = (Map<?, ?>) obj;
            obj = d.get(p);
        }
        return (T) obj;
    }

    @SneakyThrows(IOException.class)
    public static Path tar(Path source, Predicate<Path> ignore) {
        if (!Files.isDirectory(source)) {
            throw new IOException("Please provide a directory.");
        }
        // get folder name as zip file name
        final Path tarFilePath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(String.format("build_archive_%s.tar.gz", UUID.randomUUID()));
        try (final OutputStream fOut = Files.newOutputStream(tarFilePath);
             final BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
             final GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut);
             final TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {
            tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink() || ignore.test(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                    // only copy files, no symbolic links
                    if (attributes.isSymbolicLink() || ignore.test(path)) {
                        return FileVisitResult.CONTINUE;
                    }
                    // get filename
                    final Path targetFile = source.relativize(path);
                    try {
                        final TarArchiveEntry tarEntry = new TarArchiveEntry(path.toFile(), targetFile.toString());
                        tOut.putArchiveEntry(tarEntry);
                        Files.copy(path, tOut);
                        tOut.closeArchiveEntry();
                        AzureMessager.getMessager().progress(AzureString.format("compressing : %s", path));
                    } catch (final IOException e) {
                        AzureMessager.getMessager().warning(AzureString.format("Unable to compress : %s", path));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.printf("Unable to tar.gz : %s%n%s%n", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
            tOut.finish();
        }
        return tarFilePath;
    }
}
