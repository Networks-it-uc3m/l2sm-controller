package org.l2sm.vnets.api;

import org.onosproject.net.ConnectPoint;

public interface IDCOService {

    public void createVirtualNetwork(String networkId) throws IDCOServiceException;

    public void createNetworkCuyito(String networkId);

    public void deleteNetworkCuyito(String networkId);

    public Integer getNetworkCuyito(String networkId);

    public void deleteVirtualNetwork(String networkId) throws IDCOServiceException;

    public void addPort(String networkId, ConnectPoint networkEndpoint) throws IDCOServiceException;

    public Network getVirtualNetwork(String networkId) throws IDCOServiceException;

}
