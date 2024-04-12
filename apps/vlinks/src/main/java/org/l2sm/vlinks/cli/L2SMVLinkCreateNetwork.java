package org.l2sm.vlinks.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vlinks.api.IDCOVLinkService;
import org.onosproject.cli.AbstractShellCommand;

@Service
@Command(scope = "onos", name = "l2sm-vlink-create-network", description = "Create a Vlink network")


public class L2SMVLinkCreateNetwork extends AbstractShellCommand {

    @Argument(index = 0, name = "networkVlinkId", description = "networkVlinkId", required = true, multiValued = false)
    String networkVlinkId = null;

    @Override
    protected void doExecute() {
        print("Creating a new Network...");
        IDCOVLinkService idcoVlinkService = get(IDCOVLinkService.class);
        try {
            idcoVlinkService.createVLinkNetwork(networkVlinkId);
            print("Success! Network:" + networkVlinkId + " has been created");
        } catch (Exception e) {
            print("Error ocurred");
            print(e.toString());
        }
    }

}
