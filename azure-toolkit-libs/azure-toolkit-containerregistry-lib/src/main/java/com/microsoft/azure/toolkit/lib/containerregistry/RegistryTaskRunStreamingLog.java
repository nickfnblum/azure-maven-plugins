/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.containerregistry;

import com.azure.resourcemanager.containerregistry.models.RegistryTaskRun;
import com.microsoft.azure.toolkit.lib.common.utils.StreamingLogSupport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistryTaskRunStreamingLog implements StreamingLogSupport {
    private static final int RETRY_INTERVAL = 1000;
    private static final int WAIT_INTERVAL = 500;

    private RegistryTaskRun task;
    private String logSasUrl;

    @NotNull
    @Override
    public String getDisplayName() {
        return task.runId();
    }

    @NotNull
    @Override
    public String getId() {
        return task.runId();
    }

    @Override
    public Flux<String> streamingLogs(final boolean follow, @NotNull final Map<String, String> p) {
        return Flux.create(sink -> {
            String content = StringUtils.EMPTY;
            try {
                for (int i = 0; ; ) {
                    final String newContent = StringUtils.substringBeforeLast(readFromUrl(logSasUrl), "\n");
                    if (StringUtils.equals(newContent, content)) {
                        i++;
                        Thread.sleep(RETRY_INTERVAL * i);
                        continue;
                    }
                    Arrays.stream(StringUtils.removeStart(newContent, content).split("\n")).forEach(sink::next);
                    content = newContent;
                    i = 0; // reset retry count if there are new content
                    Thread.sleep(WAIT_INTERVAL);
                }
            } catch (final Exception e) {
                sink.error(e);
            }
        });
    }

    public static String readFromUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
