package com.task05.dto;

import lombok.*;

import java.util.Map;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Events {
    private String id;
    private int principalID;
    private String createdAt;
    private Map<String, String> body;
}
