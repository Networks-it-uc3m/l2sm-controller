package org.l2sm.vnets.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vnets.api.IDCOService;
import org.l2sm.vnets.api.IDCOServiceException;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;

@Service
@Command(scope = "onos", name = "l2sm-add-mirroring-port",
        description = "Add or replace the mirroring port of an existing network")
public class L2SMAddMirroringPort extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "networkId", required = true, multiValued = false)
    String networkId = null;

    @Argument(index = 1, name = "mirrorPort", description = "mirrorPort", required = true, multiValued = false)
    String mirrorPort = null;

    @Override
    protected void doExecute() {
        IDCOService idcoService = get(IDCOService.class);
        try {
            idcoService.addMirroringPort(networkId, ConnectPoint.deviceConnectPoint(mirrorPort));
            print("Mirroring port " + mirrorPort + " configured for network " + networkId + " successfully.");
        } catch (IDCOServiceException e) {
            print("Error configuring mirroring port: " + e.getMessage());
        } catch (Exception e) {
            print("Unexpected error occurred: " + e.toString());
        }
    }
}
