package org.l2sm.vnets.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vnets.api.IDCOService;
import org.l2sm.vnets.api.IDCOServiceException;
import org.l2sm.vnets.api.Network;
import org.onosproject.cli.AbstractShellCommand;

@Service
@Command(scope = "onos", name = "l2sm-get-network-cuyito", description = "Retrieve a network")

public class L2SMGetNetworkCuyito extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "networkId", required = true, multiValued = false)
    String networkId = null;

    @Override
    protected void doExecute() {
        IDCOService idcoService = get(IDCOService.class);
        Integer integ = idcoService.getNetworkCuyito(networkId);
        print(integ.toString());
    }
}
