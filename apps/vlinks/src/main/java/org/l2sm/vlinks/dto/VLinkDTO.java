package org.l2sm.vlinks.dto;

import java.util.ArrayList;

public class VLinkDTO  {

    private String linkNetworkId;

    private String fromEndP;

    private String toEndP;

    private ArrayList<String> path;

    public String getLinkNetworkId() {
        return linkNetworkId;
    }

    public void setLinkNetworkId(String linkNetworkId) {
        this.linkNetworkId = linkNetworkId;
    }

    public String getFromEndP (){
        return fromEndP;
    }

    public void setFromEndP (String fromEndP) {
        this.fromEndP = fromEndP;
    }

    public String getToEndP() {
        return toEndP;
    }

    public void setToEndP (String toEndP){
        this.toEndP = toEndP;
    }

    public ArrayList<String> getPath(){
        return path;
    }

    public void setPath(ArrayList<String> path) {
        this.path = path;
    }

    
}
