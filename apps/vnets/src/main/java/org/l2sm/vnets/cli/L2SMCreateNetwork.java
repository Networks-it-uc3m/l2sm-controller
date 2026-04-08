package org.l2sm.vnets.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vnets.api.IDCOService;
import org.l2sm.vnets.api.IDCOServiceException;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;

@Service
@Command(scope = "onos", name = "l2sm-create-network", description = "Create a network")

public class L2SMCreateNetwork extends AbstractShellCommand {

    @Argument(index = 0, name = "networkId", description = "networkId", required = true, multiValued = false)
    String networkId = null;

    @Option(name = "-m", aliases = "--mirror-port", description = "mirror port connect point", required = false,
            multiValued = false)
    String mirrorPort = null;

    @Override
    protected void doExecute() {
        IDCOService idcoService = get(IDCOService.class);
        try {
            ConnectPoint parsedMirrorPort = mirrorPort == null ? null : ConnectPoint.deviceConnectPoint(mirrorPort);
            idcoService.createVirtualNetwork(networkId, parsedMirrorPort);
            print("Network " + networkId + " created successfully.");
        } catch (IDCOServiceException e) {
            print("Error creating network: " + e.getMessage());
        } catch (Exception e) {
            print("Unexpected error occurred: " + e.toString());
        }
    }
}
