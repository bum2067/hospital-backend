package com.hospital.schedule.mapper;

import com.hospital.schedule.dtos.EmployeeDto;
import com.hospital.schedule.dtos.EmployeeRequestDto;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface EmployeeMapper {
    List<EmployeeDto> findAll();
    EmployeeDto findById(Long id);
    void insert(EmployeeRequestDto dto);
    void update(EmployeeDto dto);
    void delete(Long id);
    
}
