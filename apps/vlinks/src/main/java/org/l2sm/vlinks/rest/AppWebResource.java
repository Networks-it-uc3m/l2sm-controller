/*
 * Copyright 2024-present Open Networking Foundation
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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;

import javax.ws.rs.GET;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.l2sm.vlinks.api.IDCOVLinkService;
import org.l2sm.vlinks.api.IDCOVLinkServiceException;
import org.l2sm.vlinks.api.VLinkNetwork;
import org.l2sm.vlinks.dto.VLinkDTO;
import org.onosproject.net.ConnectPoint;

import java.util.ArrayList;

/**
 * Sample web resource.
 */
@Path("/api")
public class AppWebResource extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private IDCOVLinkService idcoVlinkService = get(IDCOVLinkService.class);

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */
    @GET
    @Path("")
    public Response getGreeting() {
        ObjectNode node = mapper().createObjectNode().put("hello", "world");
        return ok(node).build();
    }


    // @POST
    // @Path("/prueba")
    // @Consumes({"application/json",MediaType.APPLICATION_JSON})
    // public Response pueba(SampleDTO sampleDTO) throws Exception {
    //     log.info("API REST works: \nSample: " + sampleDTO.gethello());
    //     return Response.ok().build();
        
    // }

    /**
     * Implementation of get a specific Network
     *
     * @return 200 OK
     */
    @GET
    @Path("/{networkVlinkId}")
    @Produces({"application/yaml",MediaType.APPLICATION_JSON})
    public Response getNetworkById(@PathParam("networkVlinkId") String networkVlinkId) throws Exception {
        
        VLinkNetwork network = idcoVlinkService.getVLinkNetwork(networkVlinkId);

     
        if(network==null) return Response.status(Status.NOT_FOUND).build();

        return Response.status(Status.OK).entity(network).build();
    }



     /**
     * The idco is up and running
     *
     * @return 200 OK
     */
    @GET
    @Path("/status")
    public Response getStatus() throws Exception {
            
        return Response.status(Status.OK).build();
    }

    @POST
    @Path("/network")
    @Consumes({"application/yaml",MediaType.APPLICATION_JSON})
    public Response createNetwork(VLinkDTO VLinkNetworkDTO) throws Exception {
        
        
        String networkVlinkId = VLinkNetworkDTO.getLinkNetworkId();
        String fromEndPoint = VLinkNetworkDTO.getFromEndP();
        String toEndPoint = VLinkNetworkDTO.getToEndP();
        ArrayList<String> vLinkPath = VLinkNetworkDTO.getPath();

        try {
            idcoVlinkService.createVLinkNetwork(networkVlinkId, ConnectPoint.deviceConnectPoint(fromEndPoint), ConnectPoint.deviceConnectPoint(toEndPoint),  vLinkPath.toArray(new String[0])); 
            
        } catch (IDCOVLinkServiceException e){
            throw new WebApplicationException(Response.status(Status.CONFLICT).build());
        }

        return Response.status(Status.NO_CONTENT).build();
    }

    /**
     * Implementation of the Terminate Network operation
     *
     * @return 200 OK
     */
    @DELETE
    @Path("/{networkVlinkId}")
    public Response deleteNetwork(@PathParam("networkVlinkId") String networkVlinkId) throws Exception {

        idcoVlinkService.deleteVLinkNetwork(networkVlinkId);
    
        return Response.status(Status.NO_CONTENT).build();
    }

    // /**
    //  * Implementation of the Terminate Network operation
    //  *
    //  * @return 200 OK
    //  */

    //  public static class SampleDTO {
    //     private String hello;
    
    //     public String gethello() {
    //         return hello;
    //     }
    
    //     public void sethello(String hello) {
    //         this.hello = hello;
    //     }
    // }
    

}
