package com.hospital.schedule.dtos;

import lombok.Data;

@Data
public class ShiftUpdateDto {
    private Long employeeId;
    private String date;       // "2025-11-05" 형식
    private Long shiftTypeId;  // 1=D, 2=E, 3=N, 4=O
}
