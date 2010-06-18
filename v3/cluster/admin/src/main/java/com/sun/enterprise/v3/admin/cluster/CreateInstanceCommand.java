/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.enterprise.v3.admin.cluster;

import com.sun.enterprise.config.serverbeans.Node;
import com.sun.enterprise.universal.process.ProcessManagerException;
import org.glassfish.api.ActionReport;
import com.sun.enterprise.universal.process.LocalAdminCommand;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.Cluster;
import org.glassfish.api.admin.CommandRunner;
import org.glassfish.api.admin.CommandRunner.CommandInvocation;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.cluster.ssh.launcher.SSHLauncher;
import org.glassfish.cluster.ssh.connect.RemoteConnectHelper;
import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.*;
import java.util.logging.Logger;

/**
 * Remote AdminCommand to create an instance.  This command is run only on DAS.
 *  1. Register the instance on DAS
 *  2. Create the file system on the instance node via ssh, node agent, or other
 *
 * @author Jennifer Chou
 */
@Service(name = "create-instance")
@I18n("create.instance")
@Scoped(PerLookup.class)
@Cluster({RuntimeType.DAS})
public class CreateInstanceCommand implements AdminCommand {
    @Inject
    private CommandRunner cr;
    
    @Inject
    Habitat habitat;

    @Inject
    Node[] nodes;

    @Param(name="node", optional=true)
    String node;

    @Param(name="nodeagent")
    String nodeAgent;

    @Param(name = "config", optional=true)
    String configRef;

    @Param(name="cluster", optional=true)
    String clusterName;

    @Param(name = "systemproperties", optional = true, separator = ':')
    private String systemProperties;

    @Param(name = "instance_name", primary = true)
    private String instance;

    private Logger logger;
    private AdminCommandContext ctx;

    @Override
    public void execute(AdminCommandContext context) {
        ActionReport report = context.getActionReport();
        ctx = context;
        logger= context.logger;
        
        CommandInvocation ci = cr.getCommandInvocation("_register-instance", report);
        ParameterMap map = new ParameterMap();
        map.add("node", node);        
        map.add("nodeagent", nodeAgent);
        map.add("config", configRef);
        map.add("cluster", clusterName);
        map.add("systemproperties", systemProperties);
        map.add("DEFAULT", instance);
        ci.parameters(map);
        ci.execute();

        if (node.equals("localhost")) {
            LocalAdminCommand lac = new LocalAdminCommand("_create-instance-filesystem",instance);
            try {
                 int status = lac.execute();
            }   catch (ProcessManagerException ex)  {
                String msg = Strings.get("create.instance.failed", instance);
                logger.warning(msg);
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                report.setMessage(msg);                
            }

        } else {
            createInstanceRemote();
        }
        
    }

    private void createInstanceRemote() {
            RemoteConnectHelper rch = new RemoteConnectHelper(habitat, nodes, logger);
            // check if needs a remote connection
            if (rch.isRemoteConnectRequired(node)) {
                // this command will run over ssh
                int status = rch.runCommand(node, "_create-instance-filesystem", instance);
                if (status != 1){
                    ActionReport report = ctx.getActionReport();
                    String msg = Strings.get("create.instance.failed", instance);
                    logger.warning(msg);
                    report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                    report.setMessage(msg);
                }

            }
    }
}
