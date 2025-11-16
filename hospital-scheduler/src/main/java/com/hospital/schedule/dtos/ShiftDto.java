package com.hospital.schedule.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftDto {
    private Long id;
    private Long employeeId;
    private Long shiftTypeId;
    private LocalDate workDate;

    // 추가
    private String employeeName;
    private String shiftTypeName;
}
