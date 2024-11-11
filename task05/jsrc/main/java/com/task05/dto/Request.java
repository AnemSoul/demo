package com.task05.dto;

import lombok.*;
import java.util.Map;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Request {
    private int principalID;
    private Map<String, String> content;
}
