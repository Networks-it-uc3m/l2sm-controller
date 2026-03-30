/*
 * Copyright 2026 Universidad Carlos III de Madrid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.l2sm.vlinks.rest;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.l2sm.vlinks.api.IDCOVLinkService;
import org.l2sm.vlinks.api.IDCOVLinkServiceException;
import org.l2sm.vlinks.api.VLinkNetwork;
import org.l2sm.vlinks.dto.VLinkDTO;
import org.onosproject.net.ConnectPoint;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The VLink management REST API.
 */
@Path("/api")
public class NetworkManagement extends AbstractWebResource {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final IDCOVLinkService idcoVLinkService = get(IDCOVLinkService.class);

    /**
     * Retrieves a specific virtual link network.
     *
     * @param networkVlinkId virtual link identifier
     * @return 200 if found, 404 otherwise
     */
    @GET
    @Path("/{networkVlinkId}")
    @Produces({ "application/yaml", MediaType.APPLICATION_JSON })
    public Response getNetworkById(@PathParam("networkVlinkId") String networkVlinkId) {
        log.info("API REST tries to retrieve vlink network {}", networkVlinkId);

        try {
            VLinkNetwork network = idcoVLinkService.getVLinkNetwork(networkVlinkId);
            if (network == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            return Response.status(Status.OK).entity(network.toString()).build();
        } catch (IDCOVLinkServiceException e) {
            log.error("Error retrieving vlink network {}", networkVlinkId, e);
            return Response.status(Status.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Unexpected error retrieving vlink network {}", networkVlinkId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Returns API status.
     *
     * @return 200 if the API is available
     */
    @GET
    @Path("/status")
    public Response getStatus() {
        return Response.status(Status.OK).build();
    }

    /**
     * Creates a virtual link network.
     *
     * @param vLinkDTO request body
     * @return 204 if created
     */
    @POST
    @Consumes({ "application/yaml", MediaType.APPLICATION_JSON })
    public Response createNetwork(VLinkDTO vLinkDTO) {
        String networkVlinkId = vLinkDTO.getLinkNetworkId();
        try {
            List<String> path = vLinkDTO.getPath();
            String[] vLinkPath = path == null ? new String[0] : path.toArray(new String[0]);
            idcoVLinkService.createVLinkNetwork(
                    networkVlinkId,
                    ConnectPoint.deviceConnectPoint(vLinkDTO.getFromEndP()),
                    ConnectPoint.deviceConnectPoint(vLinkDTO.getToEndP()),
                    vLinkPath);
            return Response.status(Status.NO_CONTENT).build();
        } catch (IDCOVLinkServiceException e) {
            log.error("Error creating vlink network {}", networkVlinkId, e);
            return Response.status(Status.CONFLICT).build();
        } catch (Exception e) {
            log.error("Unexpected error creating vlink network {}", networkVlinkId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes a virtual link network.
     *
     * @param networkVlinkId virtual link identifier
     * @return 204 if deleted
     */
    @DELETE
    @Path("/{networkVlinkId}")
    public Response deleteNetwork(@PathParam("networkVlinkId") String networkVlinkId) {
        try {
            idcoVLinkService.deleteVLinkNetwork(networkVlinkId);
            return Response.status(Status.NO_CONTENT).build();
        } catch (IDCOVLinkServiceException e) {
            log.error("Error deleting vlink network {}", networkVlinkId, e);
            return Response.status(Status.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Unexpected error deleting vlink network {}", networkVlinkId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}
