package com.task05.dto;

import lombok.*;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Response {
    private Integer statusCode;
    private Event event;
}
