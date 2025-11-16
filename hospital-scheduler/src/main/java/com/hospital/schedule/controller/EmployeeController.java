package com.hospital.schedule.controller;

import com.hospital.schedule.dtos.EmployeeDto;
import com.hospital.schedule.dtos.EmployeeRequestDto;
import com.hospital.schedule.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000") // React 연결 대비
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public List<EmployeeDto> getAll() {
        return employeeService.getAll();
    }

    @GetMapping("/{id}")
    public EmployeeDto getById(@PathVariable Long id) {
        return employeeService.getById(id);
    }

    @PostMapping
    public String addEmployee(@Valid @RequestBody EmployeeRequestDto dto) {
        employeeService.add(dto);
        return "직원 등록 완료!";
    }

    @PutMapping("/{id}")
    public String updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeDto dto) {
        dto.setId(id);
        employeeService.update(dto);
        return "직원 정보 수정 완료!";
    }

    @DeleteMapping("/{id}")
    public String deleteEmployee(@PathVariable Long id) {
        employeeService.delete(id);
        return "직원 삭제 완료!";
    }
}
