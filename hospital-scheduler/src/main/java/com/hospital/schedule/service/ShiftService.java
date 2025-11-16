package com.hospital.schedule.service;

import com.hospital.schedule.dtos.ShiftDto;
import com.hospital.schedule.dtos.ShiftRequestDto;
import com.hospital.schedule.mapper.ShiftMapper;
import com.hospital.schedule.dtos.ShiftUpdateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftMapper shiftMapper;
    
    private final long DAY = 1L;
    private final long EVE = 2L;
    private final long NIGHT = 3L;
    private final long OFF = 4L;

    // ✅ 기존 기능 유지
    public List<ShiftDto> getAll() {
        return shiftMapper.findAll();
    }

    public List<ShiftDto> getByEmployee(Long employeeId) {
        return shiftMapper.findByEmployee(employeeId);
    }

    public void add(ShiftRequestDto dto) {
        shiftMapper.insert(dto);
    }

    public void delete(Long id) {
        shiftMapper.delete(id);
    }
    
    public boolean updateShift(ShiftUpdateDto dto) {
        Long empId = dto.getEmployeeId();
        LocalDate date = LocalDate.parse(dto.getDate());
        Long newType = dto.getShiftTypeId();

        Long yesterday = shiftMapper.findShift(empId, date.minusDays(1));
        Long twoDaysAgo = shiftMapper.findShift(empId, date.minusDays(2));

        // N → D/E 금지
        if (yesterday != null && yesterday == NIGHT &&
                (newType == DAY || newType == EVE)) {
            return false;
        }

        // N - O - D 금지
        if (twoDaysAgo != null && yesterday != null) {
            if (twoDaysAgo == NIGHT && yesterday == OFF && newType == DAY) {
                return false;
            }
        }

        // E → D 금지
        if (yesterday != null && yesterday == EVE && newType == DAY) {
            return false;
        }

        // 문제 없으면 업데이트
        shiftMapper.updateShift(dto.getEmployeeId(), date, newType);
        return true;
    }

    /**
     * ✅ 자동 근무 배정 (3교대 버전)
     * - 직원별로 DAY → NIGHT → OFF 순환
     * - 직원마다 시작 패턴이 다르게 되어 겹치지 않음
     * - OFF는 DB 저장 생략 가능 (원하면 주석 제거)
     */
    public void autoAssign(List<Long> employeeIds, Long shiftTypeId, LocalDate start, int days) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new IllegalArgumentException("employeeIds가 비어 있습니다.");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("days 는 1 이상이어야 합니다.");
        }

        // ✅ 3교대 패턴: DAY(1) → NIGHT(2) → OFF(3)
        List<Long> pattern = List.of(1L, 2L, 3L);
        List<ShiftRequestDto> bulk = new ArrayList<>();

        for (int dayIndex = 0; dayIndex < days; dayIndex++) {
            LocalDate workDate = start.plusDays(dayIndex);

            for (int empIndex = 0; empIndex < employeeIds.size(); empIndex++) {
                Long employeeId = employeeIds.get(empIndex);

                // 직원별로 시작 시점을 달리해서 패턴 겹치지 않게
                Long typeId = pattern.get((dayIndex + empIndex) % pattern.size());

                // OFF(3)은 휴무 — DB에 넣지 않으려면 아래 주석 해제
                // if (typeId == 3L) continue;

                ShiftRequestDto dto = new ShiftRequestDto();
                dto.setEmployeeId(employeeId);
                dto.setShiftTypeId(typeId);
                dto.setWorkDate(workDate);

                bulk.add(dto);
            }
        }

        // ✅ 인서트 실행
        for (ShiftRequestDto dto : bulk) {
            shiftMapper.insert(dto);
        }
    }
}
