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

package org.apache.maven.toolchain;

import org.apache.maven.context.BuildContext;

/**
 *
 * @author mkleint
 */
public interface ToolchainManager
{

    String ROLE = ToolchainManager.class.getName(  );

    /**
     * to be called from toolchains-plugin only.. TODO split?
     */
    ToolchainPrivate[] getToolchainsForType( String type )
        throws MisconfiguredToolchainException;

    /**
     * to be used from plugins capable of working with toolchains.
     */
    Toolchain getToolchainFromBuildContext( String type,
                                            BuildContext context );

    /**
     * to be called from toolchains-plugin only.. TODO split?
     */
    void storeToolchainToBuildContext( ToolchainPrivate toolchain,
                                       BuildContext context );
}