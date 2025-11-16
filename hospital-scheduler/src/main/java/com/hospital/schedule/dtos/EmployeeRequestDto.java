package com.hospital.schedule.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class EmployeeRequestDto {
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "직무(role)는 필수입니다.")
    private String role;

    private boolean nightShiftAvailable = true;

    @Positive(message = "최대 근무 시간은 양수여야 합니다.")
    private int maxWeeklyHours = 40;
}
