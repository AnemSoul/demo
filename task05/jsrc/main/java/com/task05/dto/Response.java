package com.task05.dto;

public class Response {
    private int statusCode;
    private Object events;

    public Response() {}

    public Response(int statusCode, Object event){
        this.statusCode = statusCode;
        this.events = event;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Object getEvents() {
        return events;
    }

    public void setEvents(Object events) {
        this.events = events;
    }
}
