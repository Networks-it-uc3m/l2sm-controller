package org.l2sm.vlinks.api;

import org.onosproject.net.ConnectPoint;

public interface IDCOVLinkService {

    public void createVLinkNetwork(String networkVlinkId) throws IDCOVLinkServiceException;

    public void deleteVLinkNetwork(String networkVlinkId) throws IDCOVLinkServiceException;

    public void addVLinkPort(String networkVlinkId, ConnectPoint networkVlinkEndpoint) throws IDCOVLinkServiceException;

    public VLinkNetwork getVLinkNetwork(String networkVlinkId) throws IDCOVLinkServiceException;

}
