/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.exception;

import com.microsoft.azure.toolkit.lib.common.utils.StreamingLogSupport;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;

@Getter
public class StreamingDiagnosticsException extends AzureToolkitRuntimeException {
    private final StreamingLogSupport streamingLog;

    public StreamingDiagnosticsException(@Nonnull final String cause, @Nonnull final StreamingLogSupport streamingLog) {
        super(cause);
        this.streamingLog = streamingLog;
    }

    public StreamingDiagnosticsException(@Nonnull final String cause, @Nonnull final StreamingLogSupport streamingLog, final Object... actions) {
        super(cause, actions);
        this.streamingLog = streamingLog;
    }

    public StreamingDiagnosticsException(final String error, @NotNull final Throwable cause, @Nonnull final StreamingLogSupport streamingLog, final Object... actions) {
        super(error, cause, actions);
        this.streamingLog = streamingLog;
    }
}
