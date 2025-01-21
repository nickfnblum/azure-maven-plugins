package com.microsoft.azure.maven.containerapps;

import com.microsoft.azure.toolkit.lib.containerapps.task.DeployContainerAppTask;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class DeployMojoTest extends DeployMojo {

    @Mock
    private MavenProject project;

    @InjectMocks
    private DeployMojo deployMojo;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCheckProjectPackaging_JarPackaging() throws MojoExecutionException {
        when(project.getPackaging()).thenReturn("jar");
        assertTrue(deployMojo.checkProjectPackaging(project));
    }

    @Test
    public void testCheckProjectPackaging_PomPackaging() throws MojoExecutionException {
        when(project.getPackaging()).thenReturn("pom");
        assertFalse(deployMojo.checkProjectPackaging(project));
    }

    @Test
    public void testCheckProjectPackaging_UnsupportedPackaging() {
        when(project.getPackaging()).thenReturn("war");
        MojoExecutionException exception = assertThrows(MojoExecutionException.class, () -> {
            deployMojo.checkProjectPackaging(project);
        });
        assertEquals("`azure-container-apps:deploy` does not support maven project with packaging war, only jar is supported", exception.getMessage());
    }
}
