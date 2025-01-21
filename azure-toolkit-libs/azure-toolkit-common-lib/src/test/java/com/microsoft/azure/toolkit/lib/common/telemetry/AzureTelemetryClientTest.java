/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.telemetry;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AzureTelemetryClientTest extends AzureTelemetryClient {
    @Test
    public void anonymizePersonallyIdentifiableInformation() {
        final Map<String, String> map = new HashMap<String, String>() {{
            put("fake-password", "pwd=FAKE"); // [SuppressMessage("Microsoft.Security", "CS001:SecretInline", Justification="fake credential for test case")]
            put("fake-email", "no-reply@example.com");
            put("fake-token", "token=FAKE");
            put("fake-slack-token", "xoxp-FAKE"); // [SuppressMessage("Microsoft.Security", "CS001:SecretInline", Justification="fake credential for test case")]
            put("fake-path", "/Users/username/.AzureToolkitforIntelliJ/extensions");
            put("fake-github-token", "ghp_000000000000000000000000000000000000"); // [SuppressMessage("Microsoft.Security", "CS001:SecretInline", Justification="fake credential for test case")]
            put("fake-cli-credential", "login.exe -adminp FAKE"); // [SuppressMessage("Microsoft.Security", "CS001:SecretInline", Justification="fake credential for test case")]
        }};
        AzureTelemetryClientTest.anonymizePersonallyIdentifiableInformation(map);
        assert StringUtils.equals(map.get("fake-password"), "<REDACTED: Generic Secret>");
        assert StringUtils.equals(map.get("fake-email"), "<REDACTED: Email>");
        assert StringUtils.equals(map.get("fake-token"), "<REDACTED: Generic Secret>");
        assert StringUtils.equals(map.get("fake-slack-token"), "<REDACTED: Slack Toke>");
        assert StringUtils.equals(map.get("fake-path"), "<REDACTED: user-file-path>");
        assert StringUtils.equals(map.get("fake-github-token"), "<REDACTED: GitHub Token>");
        assert StringUtils.equals(map.get("fake-cli-credential"), "<REDACTED: CLI Credentials>");
    }
}