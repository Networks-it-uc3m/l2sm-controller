package org.l2sm.vnets.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vnets.api.IDCOService;
import org.l2sm.vnets.api.IDCOServiceException;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;

@Service
@Command(scope = "onos", name = "l2sm-del-port", description = "Delete port from an existing network")

public class L2SMDelPort extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "networkId", required = true, multiValued = false)
    String networkId = null;

    @Argument(index = 1, name = "networkEndpoint", description = "networkEndpoint", required = true, multiValued = false)
    String networkEndpoint = null;

    @Override
    protected void doExecute() {
        IDCOService idcoService = get(IDCOService.class);
        try {
            idcoService.deletePort(networkId, ConnectPoint.deviceConnectPoint(networkEndpoint));
            print("Port " + networkEndpoint + " deleted from network " + networkId + " successfully.");
        } catch (IDCOServiceException e) {
            print("Error deleting port from network: " + e.getMessage());
        } catch (Exception e) {
            print("Unexpected error occurred: " + e.toString());
        }
    }
}
