package com.hospital.schedule.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDto {
    private Long id;
    private String name;
    private String role;
    private boolean nightShiftAvailable;
    private int maxWeeklyHours;
}
