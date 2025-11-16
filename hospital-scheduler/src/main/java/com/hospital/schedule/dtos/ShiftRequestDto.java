package com.hospital.schedule.dtos;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ShiftRequestDto {
    private Long employeeId;
    private Long shiftTypeId;
    private LocalDate workDate;
}
