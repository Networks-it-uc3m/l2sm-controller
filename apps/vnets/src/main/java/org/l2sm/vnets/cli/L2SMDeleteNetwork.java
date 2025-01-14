package org.l2sm.vnets.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vnets.api.IDCOService;
import org.l2sm.vnets.api.IDCOServiceException;
import org.onosproject.cli.AbstractShellCommand;

@Service
@Command(scope = "onos", name = "l2sm-delete-network", description = "Delete a network")

public class L2SMDeleteNetwork extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "networkId", required = true, multiValued = false)
    String networkId = null;

    @Override
    protected void doExecute() {
        IDCOService idcoService = get(IDCOService.class);
        try {
            idcoService.deleteVirtualNetwork(networkId);
            print("Network " + networkId + " deleted successfully.");
        } catch (IDCOServiceException e) {
            print("Error deleting network: " + e.getMessage());
        } catch (Exception e) {
            print("Unexpected error occurred: " + e.toString());
        }
    }
}
