/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UrlStreamingLog implements StreamingLogSupport {
    private String name;
    private String endpoint;
    @Setter
    @Builder.Default
    private String authorization = StringUtils.EMPTY;
    @Setter
    @Builder.Default
    private Supplier<String> authorizationSupplier = () -> StringUtils.EMPTY;

    @NotNull
    @Override
    public String getDisplayName() {
        return this.name;
    }

    @NotNull
    @Override
    public String getId() {
        return this.endpoint;
    }

    @Override
    public String getLogStreamEndpoint() {
        return this.endpoint;
    }

    @Override
    public String getLogStreamAuthorization() {
        return Optional.ofNullable(authorization)
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> Optional.ofNullable(authorizationSupplier).map(Supplier::get).orElse(StringUtils.EMPTY));
    }
}
