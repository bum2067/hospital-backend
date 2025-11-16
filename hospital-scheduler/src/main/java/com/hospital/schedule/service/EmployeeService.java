package com.hospital.schedule.service;

import com.hospital.schedule.dtos.EmployeeDto;
import com.hospital.schedule.dtos.EmployeeRequestDto;
import com.hospital.schedule.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeMapper employeeMapper;

    public List<EmployeeDto> getAll() {
        return employeeMapper.findAll();
    }

    public EmployeeDto getById(Long id) {
        return employeeMapper.findById(id);
    }

    public void add(EmployeeRequestDto dto) {
        employeeMapper.insert(dto);
    }

    public void update(EmployeeDto dto) {
        employeeMapper.update(dto);
    }

    public void delete(Long id) {
        employeeMapper.delete(id);
    }
}
