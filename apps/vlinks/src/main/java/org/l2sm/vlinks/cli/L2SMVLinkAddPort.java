// package org.l2sm.vlinks.cli;

// import org.apache.karaf.shell.api.action.Argument;
// import org.apache.karaf.shell.api.action.Command;
// import org.apache.karaf.shell.api.action.lifecycle.Service;
// import org.l2sm.vlinks.api.IDCOVLinkService;
// import org.onosproject.cli.AbstractShellCommand;
// import org.onosproject.net.ConnectPoint;

// @Service
// @Command(scope = "onos", name = "l2sm-vlink-add-port", description = "Add a port to an existing VLink network")

// public class L2SMVLinkAddPort extends AbstractShellCommand {

//     @Argument(index = 0, name = "networkVlinkId", description = "networkVlinkId", required = true, multiValued = false)
//     String networkVlinkId = null;

//     @Argument(index = 1, name = "networkVlinkEndpoint", description = "networkVlinkEndpoint", required = true, multiValued = false)
//     String networkVlinkEndpoint = null;

//     @Override
//     protected void doExecute() {
//         IDCOVLinkService idcoVlinkService = get(IDCOVLinkService.class);
//         try {
//             idcoVlinkService.addVLinkPort(networkVlinkId, ConnectPoint.deviceConnectPoint(networkVlinkEndpoint));
//         } catch (Exception e) {
//             print("Error ocurred");
//             print(e.toString());
//         }
//     }

// }
