package com.hospital.schedule.mapper;

import com.hospital.schedule.dtos.ShiftDto;
import com.hospital.schedule.dtos.ShiftRequestDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ShiftMapper {

    List<ShiftDto> findAll();

    List<ShiftDto> findByEmployee(@Param("employeeId") Long employeeId);

    void insert(ShiftRequestDto dto);

    void delete(@Param("id") Long id);

    void deleteByRangeAndType(@Param("start") String start,
                              @Param("end") String end,
                              @Param("shiftTypeId") Long shiftTypeId);

    // ✅ Simulated Annealing 스케줄러용
    void deleteByMonth(@Param("year") int year, @Param("month") int month);
    
    Long findShift(@Param("employeeId") Long employeeId,
            @Param("date") LocalDate date);

    void updateShift(@Param("employeeId") Long employeeId,
              @Param("date") LocalDate date,
              @Param("shiftTypeId") Long shiftTypeId);
}
