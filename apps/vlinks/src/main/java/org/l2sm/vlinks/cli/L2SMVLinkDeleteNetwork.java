// package org.l2sm.vlinks.cli;

// import org.apache.karaf.shell.api.action.Argument;
// import org.apache.karaf.shell.api.action.Command;
// import org.apache.karaf.shell.api.action.lifecycle.Service;
// import org.l2sm.vlinks.api.IDCOVLinkService;
// import org.onosproject.cli.AbstractShellCommand;

// @Service
// @Command(scope = "onos", name = "l2sm-vlink-delete-network", description = "Delete a network")

// public class L2SMVLinkDeleteNetwork extends AbstractShellCommand {

//     @Argument(index = 0, name = "networkId", description = "networkId", required = true, multiValued = false)
//     String networkId = null;

//     @Override
//     protected void doExecute() {
//         IDCOVLinkService idcoVlinkService = get(IDCOVLinkService.class);
//         try {
//             idcoVlinkService.deleteVLinkNetwork(networkId);
//         } catch (Exception e) {
//             print("Error ocurred");
//             print(e.toString());
//         }
//     }

// }
