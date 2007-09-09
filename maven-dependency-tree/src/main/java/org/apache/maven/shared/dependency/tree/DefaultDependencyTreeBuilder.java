package org.apache.maven.shared.dependency.tree;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.shared.dependency.tree.traversal.CollectingDependencyNodeVisitor;

/**
 * Default implementation of <code>DependencyTreeBuilder</code>.
 * 
 * @author Edwin Punzalan
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @plexus.component role="org.apache.maven.shared.dependency.tree.DependencyTreeBuilder"
 * @see DependencyTreeBuilder
 */
public class DefaultDependencyTreeBuilder implements DependencyTreeBuilder
{
    // DependencyTreeBuilder methods ------------------------------------------

    /*
     * @see org.apache.maven.shared.dependency.tree.DependencyTreeBuilder#buildDependencyTree(org.apache.maven.project.MavenProject,
     *      org.apache.maven.artifact.repository.ArtifactRepository, org.apache.maven.artifact.factory.ArtifactFactory,
     *      org.apache.maven.artifact.metadata.ArtifactMetadataSource,
     *      org.apache.maven.artifact.resolver.ArtifactCollector)
     */
    public DependencyTree buildDependencyTree( MavenProject project, ArtifactRepository repository,
                                               ArtifactFactory factory, ArtifactMetadataSource metadataSource,
                                               ArtifactCollector collector ) throws DependencyTreeBuilderException
    {
        DependencyNode rootNode = buildDependencyTree( project, repository, factory, metadataSource, null, collector );
        
        CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
        rootNode.accept( collectingVisitor );
        
        return new DependencyTree( rootNode, collectingVisitor.getNodes() );
    }
    
    /*
     * @see org.apache.maven.shared.dependency.tree.DependencyTreeBuilder#buildDependencyTree(org.apache.maven.project.MavenProject,
     *      org.apache.maven.artifact.repository.ArtifactRepository, org.apache.maven.artifact.factory.ArtifactFactory,
     *      org.apache.maven.artifact.metadata.ArtifactMetadataSource,
     *      org.apache.maven.artifact.resolver.filter.ArtifactFilter,
     *      org.apache.maven.artifact.resolver.ArtifactCollector)
     */
    public DependencyNode buildDependencyTree( MavenProject project, ArtifactRepository repository,
                                               ArtifactFactory factory, ArtifactMetadataSource metadataSource,
                                               ArtifactFilter filter, ArtifactCollector collector )
        throws DependencyTreeBuilderException
    {
        DependencyTreeResolutionListener listener = new DependencyTreeResolutionListener();

        try
        {
            Map managedVersions = project.getManagedVersionMap();

            Set dependencyArtifacts = project.getDependencyArtifacts();

            if ( dependencyArtifacts == null )
            {
                dependencyArtifacts = project.createArtifacts( factory, null, null );
            }

            collector.collect( dependencyArtifacts, project.getArtifact(), managedVersions, repository,
                               project.getRemoteArtifactRepositories(), metadataSource, filter,
                               Collections.singletonList( listener ) );

            return listener.getRootNode();
        }
        catch ( ArtifactResolutionException exception )
        {
            throw new DependencyTreeBuilderException( "Cannot build project dependency tree", exception );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new DependencyTreeBuilderException( "Invalid dependency version for artifact "
                + project.getArtifact() );
        }
    }
}