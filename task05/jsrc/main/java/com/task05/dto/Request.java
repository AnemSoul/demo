package com.task05.dto;

import lombok.*;
import java.util.Map;

@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Request {
    private int principalId;
    private Map<String, String> content;
}
