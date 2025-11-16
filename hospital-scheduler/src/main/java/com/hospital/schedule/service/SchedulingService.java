package com.hospital.schedule.service;

import com.hospital.schedule.dtos.ShiftRequestDto;
import com.hospital.schedule.mapper.ShiftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated Annealing 기반 근무표 자동 생성
 *
 * 핵심 제약:
 *  1) 30일 기준 1인당 OFF 10~11일 수준 (daysInMonth * 10 / 30 근처)
 *  2) 최대 연속 근무 4일 이하
 *  3) 금지 패턴: N→D, N→E, N-O-D (하드 제약으로 완전 차단)
 *
 * 보조 제약:
 *  - 평일: D=3, E=2, N=2
 *  - 주말/공휴일: D=2, E=2, N=2  (커버리지 만족 못하면 큰 패널티)
 */
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final ShiftMapper shiftMapper;

    // === 근무 타입 ID (DB 고정값 기준) ===
    private static final long DAY   = 1L;
    private static final long EVE   = 2L;
    private static final long NIGHT = 3L;
    private static final long OFF   = 4L;

    // 🔥 시뮬레이티드 어닐링 파라미터
    private static final double INITIAL_T          = 120.0;
    private static final double COOLING_RATE       = 0.985;
    private static final int    MAX_ITER           = 10_000;
    private static final int    MAX_NO_IMPROVEMENT = 1_200;

    // 🧩 제약조건 파라미터
    private static final int MAX_CONSEC_WORK_DAYS = 4; // 최대 연속 근무일

    // 커버리지 (Day/Eve/Night 최소 인원: false=평일, true=주말/공휴일)
    private static final Map<Boolean, int[]> COVERAGE = Map.of(
            false, new int[]{3, 2, 2}, // 평일: D=3, E=2, N=2
            true,  new int[]{2, 2, 2}  // 주말/공휴일: D=2, E=2, N=2
    );

    // 🏗️ 페널티 가중치
    private static final double W_COVERAGE   = 500.0; // 커버리지 부족
    private static final double W_CONSEC     = 250.0; // 연속 근무 초과
    private static final double W_OFF_COUNT  = 60.0;  // OFF 개수 목표와의 차이
    private static final double W_BALANCE    = 10.0;  // D/E/N 균형(표준편차)
    private static final double W_OFF_STD    = 20.0;  // OFF 균형(표준편차)

    // 직원 요청 데이터 구조(현재는 사용하지 않지만 시그니처 유지용)
    public record ShiftRequest(long employeeId, LocalDate date, long shiftTypeId) {}

    /**
     * 월 단위 최적 근무표 생성 (Simulated Annealing)
     */
    public void generateMonthlySchedule(
            int year, int month,
            List<Long> employeeIds,
            Set<LocalDate> holidays,
            Map<Long, List<ShiftRequest>> requests
    ) {
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        int empCount    = employeeIds.size();

        if (empCount < 7) {
            System.out.println("[경고] 권장 최소 인원은 7명 이상입니다 (현재: " + empCount + ")");
        }

        // 1️⃣ 초기 해 생성 (금지 패턴 최대한 피해서)
        long[][] init = createInitialSchedule(year, month, employeeIds, holidays);
        Solution current = new Solution(year, month, employeeIds, init, holidays, requests);
        Solution best    = current.copy();

        double temp      = INITIAL_T;
        double currScore = evaluate(current);
        double bestScore = currScore;
        int    noImprove = 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 2️⃣ Simulated Annealing 루프
        for (int iter = 0; iter < MAX_ITER && temp > 0.1; iter++) {
            Solution neighbor = current.copy();

            // 금지 패턴을 깨지 않는 neighbor만 사용
            if (!makeNeighbor(neighbor, rnd)) {
                continue; // 유효한 이웃을 못 만들면 이번 iteration skip
            }

            double nextScore = evaluate(neighbor);

            if (accept(currScore, nextScore, temp, rnd)) {
                current   = neighbor;
                currScore = nextScore;

                if (nextScore < bestScore) {
                    best      = neighbor.copy();
                    bestScore = nextScore;
                    noImprove = 0;
                } else {
                    noImprove++;
                }
            } else {
                noImprove++;
            }

            temp *= COOLING_RATE;

            if (noImprove > MAX_NO_IMPROVEMENT) {
                temp      = INITIAL_T;
                noImprove = 0;
            }
        }

        // 3️⃣ 마지막으로 하드 패턴(N→D/E, N-O-D)이 남아 있으면 안전하게 고치는 후처리
        fixHardPatterns(best);

        // 4️⃣ DB 저장
        shiftMapper.deleteByMonth(year, month);
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = LocalDate.of(year, month, d);
            for (int i = 0; i < empCount; i++) {
                long type = best.grid[i][d];
                ShiftRequestDto dto = new ShiftRequestDto();
                dto.setEmployeeId(employeeIds.get(i));
                dto.setShiftTypeId(type);
                dto.setWorkDate(date);
                shiftMapper.insert(dto);
            }
        }
    }

    // ------------------------------------
    // 🔹 초기 해 생성
    //  - 하루마다 D/E/N 최소 인원 채우고 나머지는 OFF
    //  - 가능한 한 N→D/E, N-O-D 피해서 배치
    // ------------------------------------
    private long[][] createInitialSchedule(
            int year, int month, List<Long> empIds, Set<LocalDate> holidays
    ) {
        YearMonth ym = YearMonth.of(year, month);
        int days = ym.lengthOfMonth();
        int E    = empIds.size();
        long[][] grid = new long[E][days + 1];

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (int d = 1; d <= days; d++) {
            LocalDate date = LocalDate.of(year, month, d);
            boolean wknd  = isWeekendOrHoliday(date, holidays);

            int[] req = Arrays.copyOf(COVERAGE.get(wknd), 3); // {D,E,N}
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < E; i++) order.add(i);
            Collections.shuffle(order);

            // helper: 이 직원에게 오늘 type을 주면 하드 패턴 깨지는지?
            for (int shiftIdx = 0; shiftIdx < 3; shiftIdx++) { // 0:D,1:E,2:N
                long type = (shiftIdx == 0 ? DAY : (shiftIdx == 1 ? EVE : NIGHT));
                int need  = req[shiftIdx];
                if (need <= 0) continue;

                for (int idx : order) {
                    if (need == 0) break;
                    if (grid[idx][d] != 0) continue;

                    if (isHardPatternIfAssign(grid, idx, d, type)) {
                        continue; // 이 사람한테 이 타입 주면 하드 패턴 깨짐
                    }
                    grid[idx][d] = type;
                    need--;
                }
                req[shiftIdx] = need;
            }

            // 아직 커버리지 못 채웠으면, 패턴 무시하고 강제로 채움 (나중에 SA & 후처리에서 보정)
            for (int shiftIdx = 0; shiftIdx < 3; shiftIdx++) {
                long type = (shiftIdx == 0 ? DAY : (shiftIdx == 1 ? EVE : NIGHT));
                int need  = req[shiftIdx];
                if (need <= 0) continue;

                for (int idx : order) {
                    if (need == 0) break;
                    if (grid[idx][d] != 0) continue;
                    grid[idx][d] = type;
                    need--;
                }
                req[shiftIdx] = need;
            }

            // 남은 사람은 OFF
            for (int idx : order) {
                if (grid[idx][d] == 0) {
                    grid[idx][d] = OFF;
                }
            }
        }

        return grid;
    }

    private boolean isWeekendOrHoliday(LocalDate date, Set<LocalDate> holidays) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY
                || (holidays != null && holidays.contains(date));
    }

    // ------------------------------------
    // 🔹 이웃 해 생성 (금지 패턴을 깨지 않는 선에서 swap 또는 변경)
    // ------------------------------------
    private boolean makeNeighbor(Solution s, ThreadLocalRandom rnd) {
        int E = s.empIds.size();
        int D = s.daysInMonth;

        // 여러 번 시도해 보다가 유효한 변경 못 찾으면 false
        for (int attempt = 0; attempt < 30; attempt++) {
            int day = 1 + rnd.nextInt(D);

            if (rnd.nextDouble() < 0.6) {
                // 같은 날짜에 두 사람 근무 교환
                int a = rnd.nextInt(E);
                int b = rnd.nextInt(E);
                if (a == b) continue;

                long tA = s.grid[a][day];
                long tB = s.grid[b][day];
                s.grid[a][day] = tB;
                s.grid[b][day] = tA;

                if (!violatesHardRule(s)) {
                    return true;
                }

                // 되돌리기
                s.grid[a][day] = tA;
                s.grid[b][day] = tB;
            } else {
                // 한 사람의 특정 날짜 근무 변경
                int e = rnd.nextInt(E);
                long old = s.grid[e][day];
                long[] options = new long[]{DAY, EVE, NIGHT, OFF};
                long neo = options[rnd.nextInt(options.length)];
                if (neo == old) continue;

                s.grid[e][day] = neo;
                if (!violatesHardRule(s)) {
                    return true;
                }
                s.grid[e][day] = old;
            }
        }
        return false;
    }

    /**
     * grid[idx][day]에 type을 배치하면 하드 패턴(N→D/E, N-O-D)을 만드는지 검사
     */
    private boolean isHardPatternIfAssign(long[][] grid, int idx, int day, long type) {
        long prev1 = (day > 1) ? grid[idx][day - 1] : OFF;
        long prev2 = (day > 2) ? grid[idx][day - 2] : OFF;

        // N → D/E 금지
        if (prev1 == NIGHT && (type == DAY || type == EVE)) return true;

        // N-O-D 금지
        if (prev2 == NIGHT && prev1 == OFF && type == DAY) return true;

        // 👉 새로 추가: E → D 금지
        if (prev1 == EVE && type == DAY) return true;

        return false;
    }


    /**
     * 현재 grid가 하드 패턴(N→D/E, N-O-D)을 포함하는지 검사
     */
    private boolean violatesHardRule(Solution s) {
        int E = s.empIds.size();
        int D = s.daysInMonth;

        for (int i = 0; i < E; i++) {
            for (int d = 2; d <= D; d++) {
                long yesterday = s.grid[i][d - 1];
                long today = s.grid[i][d];

                // N -> D/E 금지
                if (yesterday == NIGHT && (today == DAY || today == EVE)) {
                    return true;
                }

                // N O D 금지
                if (d >= 3) {
                    long t2 = s.grid[i][d - 2];
                    long t1 = s.grid[i][d - 1];
                    long t0 = s.grid[i][d];
                    if (t2 == NIGHT && t1 == OFF && t0 == DAY) {
                        return true;
                    }
                }

                // 👉 새로 추가: E -> D 금지
                if (yesterday == EVE && today == DAY) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * 마지막 안전장치: 혹시 남아 있는 N→D/E, N-O-D 패턴을 OFF로 강제로 끊어 줌
     */
    private void fixHardPatterns(Solution s) {
        int E = s.empIds.size();
        int D = s.daysInMonth;

        for (int i = 0; i < E; i++) {
            for (int d = 2; d <= D; d++) {
                long y = s.grid[i][d - 1];
                long t = s.grid[i][d];

                // N → D/E 는 오늘을 OFF로 바꿔서 끊음
                if (y == NIGHT && (t == DAY || t == EVE)) {
                    s.grid[i][d] = OFF;
                }

                // N-O-D 는 마지막 D 를 OFF로 변경
                if (d >= 3) {
                    long t2 = s.grid[i][d - 2];
                    long t1 = s.grid[i][d - 1];
                    long t0 = s.grid[i][d];
                    if (t2 == NIGHT && t1 == OFF && t0 == DAY) {
                        s.grid[i][d] = OFF;
                    }
                }
            }
        }
    }

    // ------------------------------------
    // 🔹 SA 수용 함수
    // ------------------------------------
    private boolean accept(double curr, double next, double T, ThreadLocalRandom rnd) {
        if (next < curr) return true;
        double delta = next - curr;
        return rnd.nextDouble() < Math.exp(-delta / T);
    }

    // ------------------------------------
    // 🔹 평가 함수
    // ------------------------------------
    private double evaluate(Solution s) {
        double score = 0.0;
        int days = s.daysInMonth;
        int empCount = s.empIds.size();

        // 0️⃣ 목표 OFF 개수 (30일 기준 10일 → days * (10/30))
        double offTargetPerPerson = days * (10.0 / 30.0);

        // 1️⃣ 날짜별 커버리지
        for (int d = 1; d <= days; d++) {
            LocalDate date = LocalDate.of(s.year, s.month, d);
            boolean wknd = isWeekendOrHoliday(date, s.holidays);
            int[] req = COVERAGE.get(wknd);

            int cD = 0, cE = 0, cN = 0;
            for (int i = 0; i < empCount; i++) {
                long t = s.grid[i][d];
                if (t == DAY)   cD++;
                if (t == EVE)   cE++;
                if (t == NIGHT) cN++;
            }

            if (cD < req[0]) score += (req[0] - cD) * W_COVERAGE;
            if (cE < req[1]) score += (req[1] - cE) * W_COVERAGE;
            if (cN < req[2]) score += (req[2] - cN) * W_COVERAGE;
        }

        // 2️⃣ 직원별 OFF 개수 / 연속 근무
        List<Integer> offCounts = new ArrayList<>();
        for (int i = 0; i < empCount; i++) {
            int offCnt     = 0;
            int workStreak = 0;

            for (int d = 1; d <= days; d++) {
                long t = s.grid[i][d];
                if (t == OFF) {
                    offCnt++;
                    workStreak = 0;
                } else {
                    workStreak++;
                    if (workStreak > MAX_CONSEC_WORK_DAYS) {
                        score += (workStreak - MAX_CONSEC_WORK_DAYS) * W_CONSEC;
                    }
                }
            }

            // OFF 개수가 목표와 얼마나 다른지
            score += Math.abs(offCnt - offTargetPerPerson) * W_OFF_COUNT;
            offCounts.add(offCnt);
        }

        // OFF 균등 분배(표준편차)
        score += stdDev(offCounts) * W_OFF_STD;

        // 3️⃣ 근무유형별 균등 분배(옵션)
        score += (stdDev(countPerType(s, DAY))
                + stdDev(countPerType(s, EVE))
                + stdDev(countPerType(s, NIGHT))) * W_BALANCE;

        return score;
    }

    private List<Integer> countPerType(Solution s, long type) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < s.empIds.size(); i++) {
            int cnt = 0;
            for (int d = 1; d <= s.daysInMonth; d++) {
                if (s.grid[i][d] == type) cnt++;
            }
            result.add(cnt);
        }
        return result;
    }

    private double stdDev(List<Integer> values) {
        if (values.isEmpty()) return 0.0;
        double mean = values.stream().mapToDouble(v -> v).average().orElse(0);
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .sum() / values.size();
        return Math.sqrt(variance);
    }

    // 내부 해 표현
    private static class Solution {
        final int year, month, daysInMonth;
        final List<Long> empIds;
        final Set<LocalDate> holidays;
        final Map<Long, List<ShiftRequest>> requests;
        long[][] grid;

        Solution(int year, int month, List<Long> empIds, long[][] grid,
                 Set<LocalDate> holidays, Map<Long, List<ShiftRequest>> requests) {
            this.year        = year;
            this.month       = month;
            this.daysInMonth = YearMonth.of(year, month).lengthOfMonth();
            this.empIds      = empIds;
            this.grid        = grid;
            this.holidays    = (holidays == null) ? Set.of() : holidays;
            this.requests    = (requests == null) ? Map.of() : requests;
        }

        Solution copy() {
            long[][] copy = new long[grid.length][grid[0].length];
            for (int i = 0; i < grid.length; i++) {
                System.arraycopy(grid[i], 0, copy[i], 0, grid[i].length);
            }
            return new Solution(year, month, empIds, copy, holidays, requests);
        }
    }
}
