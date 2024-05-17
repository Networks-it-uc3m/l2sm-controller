package org.l2sm.vlinks.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.l2sm.vlinks.api.IDCOVLinkService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.DefaultLink;
import org.onlab.graph.ScalarWeight;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.Path;
import org.onosproject.net.provider.ProviderId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Service
@Command(scope = "onos", name = "l2sm-vlink-create-path", description = "Indicates the pathto follow to an existing VLink network depending on the origin and destination")

public class L2SMVLinkCreatePath extends AbstractShellCommand {

    @Argument(index = 0, name = "networkVlinkId", description = "networkVlinkId", required = true, multiValued = false)
    String networkVlinkId = null;

    @Argument(index = 1, name = "networkVlinkFromEndpoint", description = "networkVlinkFromEndpoint", required = true, multiValued = false)
    String networkVlinkFromEndpoint = null;

    @Argument(index = 2, name = "networkVlinkToEndpoint", description = "networkVlinkToEndpoint", required = true, multiValued = false)
    String networkVlinkToEndpoint = null;

    @Argument(index = 3, name = "vLinkPath", description = "vLinkPath", required = true, multiValued = false)
    String[] vLinkPath;

    @Override
    protected void doExecute() {
        IDCOVLinkService idcoVlinkService = get(IDCOVLinkService.class);
        //print("vLinkpath: " + Arrays.toString(vLinkPath));
        List<Link> linksPath = new ArrayList<>();


        for (int i = 1; i < (vLinkPath.length - 1); i++) {
            
            print("In the loop: " + i);
            print("src: " + vLinkPath[i] + " - dst: " + vLinkPath[i+1]);

            DefaultLink newlink = DefaultLink.builder()
                .providerId(new ProviderId("scheme", vLinkPath[i]))
                .src(ConnectPoint.deviceConnectPoint(vLinkPath[i]))
                .dst(ConnectPoint.deviceConnectPoint(vLinkPath[i+1]))
                .type(Link.Type.DIRECT)
                .state(Link.State.ACTIVE)
                .build();
            linksPath.add(newlink);
            i++;
            

        }
        DefaultPath vLink = new DefaultPath(new ProviderId("scheme", "id"),linksPath, new ScalarWeight(0.0));
        for (Link link : vLink.links()) {
            print(link.toString()); // Assuming Link has a meaningful toString() method
        }
        try {

            idcoVlinkService.createVLinkPath(networkVlinkId, ConnectPoint.deviceConnectPoint(networkVlinkFromEndpoint),ConnectPoint.deviceConnectPoint(networkVlinkToEndpoint), vLink); 
            

        } catch (Exception e) {
            print("Error ocurred");
            print(e.toString());
        }
    }

}
