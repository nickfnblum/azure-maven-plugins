package com.microsoft.azure.maven.containerapps.config;

import lombok.Data;

@Data
public class IngressMavenConfig {
    private Integer targetPort;
    private Boolean external;
}
