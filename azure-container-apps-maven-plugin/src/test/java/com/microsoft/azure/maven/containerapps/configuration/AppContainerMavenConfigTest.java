package com.microsoft.azure.maven.containerapps.configuration;

import com.microsoft.azure.maven.containerapps.config.AppContainerMavenConfig;
import com.microsoft.azure.maven.containerapps.config.DeploymentType;
import com.azure.resourcemanager.appcontainers.models.EnvironmentVar;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AppContainerMavenConfigTest {

    private AppContainerMavenConfig config;

    @Before
    public void setUp() {
        config = new AppContainerMavenConfig();
    }

    @Test
    public void testCpu() {
        config.setCpu(1.5);
        assertEquals(1.5, config.getCpu(), 0);
    }

    @Test
    public void testMemory() {
        config.setMemory("2Gi");
        assertEquals("2Gi", config.getMemory());
    }

    @Test
    public void testDeploymentType() {
        config.setType(DeploymentType.CODE.toString());
        assertEquals(DeploymentType.CODE, config.getDeploymentType());
    }

    @Test
    public void testImage() {
        config.setImage("my-image:latest");
        assertEquals("my-image:latest", config.getImage());
    }

    @Test
    public void testEnvironment() {
        EnvironmentVar envVar = new EnvironmentVar().withName("ENV_VAR").withValue("value");
        config.setEnvironment(Collections.singletonList(envVar));
        assertEquals(1, config.getEnvironment().size());
        assertEquals("ENV_VAR", config.getEnvironment().get(0).name());
        assertEquals("value", config.getEnvironment().get(0).value());
    }

    @Test
    public void testDirectory() {
        config.setDirectory("/app");
        assertEquals("/app", config.getDirectory());
    }

    @Test
    public void testDefaultValues() {
        assertNull(config.getCpu());
        assertNull(config.getMemory());
        assertEquals(DeploymentType.IMAGE, config.getDeploymentType());
        assertNull(config.getImage());
        assertNull(config.getEnvironment());
        assertNull(config.getDirectory());
    }
}
