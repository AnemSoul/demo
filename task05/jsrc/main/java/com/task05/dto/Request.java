package com.task05.dto;

import java.util.Map;

public class Request {
    private int principalID;
    private Map<String, String> content;

    public Map<String, String> getContent() {
        return content;
    }

    public void setContent(Map<String, String> content) {
        this.content = content;
    }

    public int getPrincipalID() {
        return principalID;
    }

    public void setPrincipalID(int principalID) {
        this.principalID = principalID;
    }

    @Override
    public String toString() {
        return "Request{" +
                "principalID=" + principalID +
                ", content=" + content +
                '}';
    }
}
