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
    private Number generationTimeMs;
    private Number utcOffsetSeconds;
    private String timezone;
    private String timezoneAbbreviation;
    private Number elevation;
    private HourlyUnits hourlyUnits;
    private Hourly hourly;

}
