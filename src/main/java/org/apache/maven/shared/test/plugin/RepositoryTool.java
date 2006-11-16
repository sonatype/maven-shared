package org.apache.maven.shared.test.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * Tools to access and manage Maven repositories for test builds, including construction of a local 
 * repository directory structure.
 * 
 * <p>
 * <b>WARNING:</b> Currently, the <code>createLocalRepositoryFromPlugin</code> method will not 
 * resolve parent POMs that exist <b>only</b> in your normal local repository, and are not reachable 
 * using the relativePath element. This may result in failed test builds, as one or more of the 
 * plugin's ancestor POMs cannot be resolved.
 * </p>
 * 
 * @plexus.component role="org.apache.maven.shared.test.plugin.RepositoryTool" role-hint="default"
 * @author jdcasey
 */
public class RepositoryTool
    implements Contextualizable
{
    public static final String ROLE = RepositoryTool.class.getName();

    /**
     * @plexus.requirement
     */
    private ArtifactRepositoryFactory repositoryFactory;

    /**
     * @plexus.requirement
     */
    private MavenSettingsBuilder settingsBuilder;

    /**
     * @plexus.requirement
     */
    private ArtifactFactory artifactFactory;

    /**
     * @plexus.requirement
     */
    private ArtifactInstaller artifactInstaller;

    // contextualized.
    private PlexusContainer container;

    /**
     * Lookup and return the location of the normal Maven local repository.
     */
    public File findLocalRepositoryDirectory() throws TestToolsException
    {
        Settings settings;
        try
        {
            settings = settingsBuilder.buildSettings();
        }
        catch ( IOException e )
        {
            throw new TestToolsException( "Error building Maven settings.", e );
        }
        catch ( XmlPullParserException e )
        {
            throw new TestToolsException( "Error building Maven settings.", e );
        }

        return new File( settings.getLocalRepository() );
    }

    /**
     * Construct an ArtifactRepository instance that refers to the normal Maven local repository.
     */
    public ArtifactRepository createLocalArtifactRepositoryInstance()
        throws TestToolsException
    {
        File localRepoDir = findLocalRepositoryDirectory();
        
        return createLocalArtifactRepositoryInstance( localRepoDir );
    }

    /**
     * Construct an ArtifactRepository instance that refers to the test-time Maven local repository.
     * @param localRepositoryDirectory The location of the local repository to be used for test builds.
     */
    public ArtifactRepository createLocalArtifactRepositoryInstance( File localRepositoryDirectory )
        throws TestToolsException
    {
        ArtifactRepositoryLayout defaultLayout;
        try
        {
            defaultLayout = (ArtifactRepositoryLayout) container.lookup( ArtifactRepositoryLayout.ROLE, "default" );
        }
        catch ( ComponentLookupException e )
        {
            throw new TestToolsException( "Error retrieving default repository layout.", e );
        }

        try
        {
            return repositoryFactory.createArtifactRepository( "local", localRepositoryDirectory.toURL()
                .toExternalForm(), defaultLayout, null, null );
        }
        catch ( MalformedURLException e )
        {
            throw new TestToolsException( "Error converting local repo directory to a URL.", e );
        }

    }

    /**
     * Install a test version of a plugin - along with its POM, and as many ancestor POMs as can be
     * reached using the &lt;relativePath/&gt; element - to a clean local repository directory for
     * use in test builds.
     * 
     * <p>
     * <b>WARNING:</b> Currently, this method will not resolve parent POMs that exist <b>only</b> in
     * your normal local repository, and are not reachable using the relativePath element. This may
     * result in failed test builds, as one or more of the plugin's ancestor POMs cannot be resolved.
     * </p>
     * 
     * @param pluginProject
     * @param targetLocalRepoBasedir
     * @throws TestToolsException
     */
    public void createLocalRepositoryFromPlugin( MavenProject pluginProject, File targetLocalRepoBasedir )
        throws TestToolsException
    {
        Artifact artifact = pluginProject.getArtifact();
        ArtifactRepository localRepository = createLocalArtifactRepositoryInstance( targetLocalRepoBasedir );

        String localPath = localRepository.pathOf( artifact );

        File destination = new File( localRepository.getBasedir(), localPath );
        if ( !destination.getParentFile().exists() )
        {
            destination.getParentFile().mkdirs();
        }

        try
        {
            artifactInstaller.install( artifact.getFile(), artifact, localRepository );
        }
        catch ( ArtifactInstallationException e )
        {
            throw new TestToolsException( "Error installing plugin artifact to target local repository: "
                + targetLocalRepoBasedir, e );
        }

        installLocallyReachableAncestorPoms( pluginProject.getFile(), localRepository );
    }

    /**
     * Traverse &lt;relativePath/&gt; links for successive POMs in the plugin's ancestry, installing
     * each one into the test-time local repository.
     * 
     * @param pomFile The plugin POM; a starting point.
     * @param localRepo The test-time local repository instance
     */
    private void installLocallyReachableAncestorPoms( File pomFile, ArtifactRepository localRepo )
        throws TestToolsException
    {
        MavenXpp3Reader pomReader = new MavenXpp3Reader();

        File pom = pomFile;

        boolean firstPass = true;

        while ( pom != null )
        {

            if ( !pom.exists() )
            {
                pom = null;
                break;
            }

            String pomGroupId = null;
            String pomArtifactId = null;
            String pomVersion = null;

            FileReader reader = null;

            File currentPom = pom;

            try
            {
                reader = new FileReader( pom );

                Model model = pomReader.read( reader );

                pomGroupId = model.getGroupId();
                pomArtifactId = model.getArtifactId();
                pomVersion = model.getVersion();

                Parent parent = model.getParent();
                if ( parent != null )
                {
                    pom = new File( pom.getParentFile(), parent.getRelativePath() );
                }
                else
                {
                    pom = null;
                }
            }
            catch ( IOException e )
            {
                throw new TestToolsException( "Error reading ancestor POM: " + currentPom, e );
            }
            catch ( XmlPullParserException e )
            {
                throw new TestToolsException( "Error reading ancestor POM: " + currentPom, e );
            }
            finally
            {
                IOUtil.close( reader );
            }

            if ( !firstPass )
            {
                Artifact pomArtifact = artifactFactory.createProjectArtifact( pomGroupId, pomArtifactId, pomVersion );
                pomArtifact.addMetadata( new ProjectArtifactMetadata( pomArtifact, currentPom ) );

                try
                {
                    artifactInstaller.install( currentPom, pomArtifact, localRepo );
                }
                catch ( ArtifactInstallationException e )
                {
                    throw new TestToolsException( "Error installing ancestor POM: " + currentPom + " to target local repository: " + localRepo.getBasedir(), e );
                }
            }
            else
            {
                firstPass = false;
            }
        }
    }

    /**
     * Retrieve the PlexusContainer instance used to instantiate this component. The container is
     * used to retrieve the default ArtifactRepositoryLayout component, for use in constructing
     * instances of ArtifactRepository that can be used to access local repositories.
     */
    public void contextualize( Context context )
        throws ContextException
    {
        this.container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

}
