package com.task09.weatherDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Forecast {
    private Number latitude;
    private Number longitude;
    private Number generationtime_ms;
    private Number utc_offset_seconds;
    private String timezone;
    private String timezone_abbreviation;
    private Number elevation;
    private HourlyUnits hourly_units;
    private Hourly hourly;

}
