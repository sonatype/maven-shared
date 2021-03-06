package org.apache.maven.model.converter.relocators;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.converter.ConverterListener;
import org.apache.maven.model.converter.ModelUtils;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A general implementation of the <code>PluginRelocator</code> interface.
 *
 * @author Dennis Lundberg
 * @version $Id$
 */
public abstract class AbstractPluginRelocator
    extends AbstractLogEnabled
    implements PluginRelocator
{
    private List listeners = new ArrayList();

    /**
     * If there is no replacement for this plugin, you can have the plugin
     * removed from the v4 pom by returning <code>null</code> from this method
     * and from getNewGroupId().
     *
     * @return The artifactId of the new Maven 2 plugin
     */
    public abstract String getNewArtifactId();

    /**
     * If there is no replacement for this plugin, you can have the plugin
     * removed from the v4 pom by returning <code>null</code> from this method
     * and from getNewArtifactId().
     *
     * @return The groupId of the new Maven 2 plugin
     */
    public abstract String getNewGroupId();

    /**
     * <strong>Note:</strong> Because we are working on the recently converted
     * Maven 2 model, this method must return the artifactId that is in the
     * model, after the model has been converted.
     *
     * @return The artifactId of the Maven 1 plugin.
     * @see org.apache.maven.model.converter.PomV3ToV4Translator#translateDependencies(java.util.List)
     */
    public abstract String getOldArtifactId();

    /**
     * <strong>Note:</strong> Because we are working on the recently converted
     * Maven 2 model, this method must return the groupId that is in the model,
     * after the model has been converted.
     * <p/>
     * Feel free to overload this method if your plugin has a different groupId.
     * </p>
     *
     * @return The groupId of the Maven 1 plugin.
     * @see org.apache.maven.model.converter.PomV3ToV4Translator#translateDependencies(java.util.List)
     */
    public String getOldGroupId()
    {
        return "org.apache.maven.plugins";
    }

    /**
     * {@inheritDoc}
     */
    public void relocate( Model v4Model )
    {
        // Relocate build plugins
        Plugin oldBuildPlugin = ModelUtils.findBuildPlugin( v4Model, getOldGroupId(), getOldArtifactId() );
        if ( oldBuildPlugin != null )
        {
            if ( getNewArtifactId() == null && getNewGroupId() == null )
            {
                // Remove the old plugin
                v4Model.getBuild().getPlugins().remove( oldBuildPlugin );
                sendInfoMessage( "Removing build plugin " + getOldGroupId() + ":" + getOldArtifactId() );
                fireRemovePluginEvent( getOldGroupId(), getOldArtifactId() );
            }
            else
            {
                Plugin newPlugin = ModelUtils.findBuildPlugin( v4Model, getNewGroupId(), getNewArtifactId() );
                if ( newPlugin == null )
                {
                    // The new plugin does not exist, relocate the old one
                    oldBuildPlugin.setArtifactId( getNewArtifactId() );
                    oldBuildPlugin.setGroupId( getNewGroupId() );
                    sendInfoMessage( "Relocating build plugin " + getOldGroupId() + ":" + getOldArtifactId() );
                    fireRelocatePluginEvent( getOldGroupId(), getOldArtifactId(), getNewGroupId(), getNewArtifactId() );
                }
                else
                {
                    // The new plugin already exist, remove the old one
                    v4Model.getBuild().getPlugins().remove( oldBuildPlugin );
                    sendInfoMessage( "Removing old build plugin " + getOldGroupId() + ":" + getOldArtifactId()
                        + " because the new one already exist" );
                    fireRemovePluginEvent( getOldGroupId(), getOldArtifactId() );
                }
            }
        }

        // Relocate report plugins
        ReportPlugin oldReportPlugin = ModelUtils.findReportPlugin( v4Model, getOldGroupId(), getOldArtifactId() );
        if ( oldReportPlugin != null )
        {
            if ( getNewArtifactId() == null && getNewGroupId() == null )
            {
                // Remove the old plugin
                v4Model.getReporting().getPlugins().remove( oldReportPlugin );
                sendInfoMessage( "Removing report plugin " + getOldGroupId() + ":" + getOldArtifactId() );
                fireRemovePluginEvent( getOldGroupId(), getOldArtifactId() );
            }
            else
            {
                ReportPlugin newPlugin = ModelUtils.findReportPlugin( v4Model, getNewGroupId(), getNewArtifactId() );
                if ( newPlugin == null )
                {
                    // The new plugin does not exist, relocate the old one
                    oldReportPlugin.setArtifactId( getNewArtifactId() );
                    oldReportPlugin.setGroupId( getNewGroupId() );
                    sendInfoMessage( "Relocating report plugin " + getOldGroupId() + ":" + getOldArtifactId() );
                    fireRelocateReportEvent( getOldGroupId(), getOldArtifactId(), getNewGroupId(), getNewArtifactId() );
                }
                else
                {
                    // The new plugin already exist, remove the old one
                    v4Model.getReporting().getPlugins().remove( oldReportPlugin );
                    sendInfoMessage( "Removing old report plugin " + getOldGroupId() + ":" + getOldArtifactId()
                        + " because the new one already exist" );
                    fireRemovePluginEvent( getOldGroupId(), getOldArtifactId() );
                }
            }
        }
    }

    public void addListener( ConverterListener listener )
    {
        if ( !listeners.contains( listener ) )
        {
            listeners.add( listener );
        }
    }

    public void addListeners( List listeners )
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            ConverterListener listener = (ConverterListener) i.next();
            addListener( listener );
        }
    }

    private void sendInfoMessage( String message )
    {
        getLogger().info( message );

        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            ConverterListener listener = (ConverterListener) i.next();
            listener.info( message );
        }
    }

    private void fireRelocatePluginEvent( String oldGroupId, String oldArtifactId, String newGroupId,
                                          String newArtifactId )
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            ConverterListener listener = (ConverterListener) i.next();
            listener.relocatePluginEvent( oldGroupId, oldArtifactId, newGroupId, newArtifactId );
        }
    }

    private void fireRelocateReportEvent( String oldGroupId, String oldArtifactId, String newGroupId,
                                          String newArtifactId )
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            ConverterListener listener = (ConverterListener) i.next();
            listener.relocateReportEvent( oldGroupId, oldArtifactId, newGroupId, newArtifactId );
        }
    }

    private void fireRemovePluginEvent( String groupId, String artifactId )
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            ConverterListener listener = (ConverterListener) i.next();
            listener.removePluginEvent( groupId, artifactId );
        }
    }

    private void fireRemoveReportEvent( String groupId, String artifactId )
    {
        for ( Iterator i = listeners.iterator(); i.hasNext(); )
        {
            ConverterListener listener = (ConverterListener) i.next();
            listener.removeReportEvent( groupId, artifactId );
        }
    }
}
