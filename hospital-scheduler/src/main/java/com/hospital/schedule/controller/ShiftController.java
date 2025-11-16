package com.hospital.schedule.controller;

import com.hospital.schedule.dtos.ShiftDto;
import com.hospital.schedule.dtos.ShiftRequestDto;
import com.hospital.schedule.dtos.ShiftUpdateDto;
import com.hospital.schedule.service.ShiftService;
import com.hospital.schedule.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShiftController {

    private final ShiftService shiftService;
    private final SchedulingService schedulingService;


    // ============================
    // 1️⃣ 기본 CRUD
    // ============================
    @GetMapping
    public List<ShiftDto> getAll() {
        return shiftService.getAll();
    }

    @GetMapping("/employee/{employeeId}")
    public List<ShiftDto> getByEmployee(@PathVariable Long employeeId) {
        return shiftService.getByEmployee(employeeId);
    }

    @PostMapping
    public String addShift(@RequestBody ShiftRequestDto dto) {
        shiftService.add(dto);
        return "근무 추가 완료!";
    }

    @DeleteMapping("/{id}")
    public String deleteShift(@PathVariable Long id) {
        shiftService.delete(id);
        return "근무 삭제 완료!";
    }


    // ============================
    // 2️⃣ 단기 테스트용 자동 배정
    // ============================
    @PostMapping("/auto")
    public String autoAssign(
            @RequestParam List<Long> employeeIds,
            @RequestParam Long shiftTypeId,
            @RequestParam String startDate,
            @RequestParam int days
    ) {
        shiftService.autoAssign(employeeIds, shiftTypeId, LocalDate.parse(startDate), days);
        return "자동 근무 배정 완료!";
    }


    // ============================
    // 3️⃣ 시뮬레이티드 어닐링 월간 자동 생성
    // ============================
    @PostMapping("/auto/monthly")
    public String generateMonthlySchedule(
            @RequestParam int year,
            @RequestParam int month,
            @RequestBody List<Long> employeeIds
    ) {
        Set<LocalDate> holidays = new HashSet<>();
        Map<Long, List<SchedulingService.ShiftRequest>> requests = new HashMap<>();

        schedulingService.generateMonthlySchedule(year, month, employeeIds, holidays, requests);
        return "✅ 시뮬레이티드 어닐링 기반 월 근무표 자동 생성 완료!";
    }


    // ============================
    // 4️⃣ 근무 수정 기능 (NEW!)
    // ============================
    @PatchMapping("/update")
    public Map<String, Object> updateShift(@RequestBody ShiftUpdateDto dto) {

        boolean ok = shiftService.updateShift(dto);

        Map<String, Object> response = new HashMap<>();

        if (!ok) {
            response.put("success", false);
            response.put("message", "❌ 금지된 패턴입니다. (N→D/E, N-O-D, E→D 불가)");
            return response;
        }

        response.put("success", true);
        response.put("message", "근무 수정 완료!");
        return response;
    }
}
