package com.hospital.schedule.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftTypeDto {
    private Long id;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
}
