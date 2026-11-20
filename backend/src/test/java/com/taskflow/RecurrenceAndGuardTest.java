package com.taskflow;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.collaboration.*;
import com.taskflow.config.RequestGuardFilter;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RecurrenceAndGuardTest {
  @Test
  void monthlyRecurrenceKeepsTheOriginalDayAcrossShortMonths() {
    var feb = RecurringTaskJob.next(LocalDate.of(2026, 1, 31), Frequency.MONTHLY, 31);
    assertThat(feb).isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(RecurringTaskJob.next(feb, Frequency.MONTHLY, 31))
        .isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  void dailyAndWeeklySchedulesCrossYearBoundaries() {
    assertThat(RecurringTaskJob.next(LocalDate.of(2026, 12, 31), Frequency.DAILY, 31))
        .isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(RecurringTaskJob.next(LocalDate.of(2026, 12, 31), Frequency.WEEKLY, 31))
        .isEqualTo(LocalDate.of(2027, 1, 7));
  }

  @Test
  void authenticationRequestsAreThrottled() throws Exception {
    var filter =
        new RequestGuardFilter(
            Set.of("http://localhost:5173"), new ObjectMapper().findAndRegisterModules(), 2);
    for (int i = 1; i <= 3; i++) {
      var req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
      req.setRemoteAddr("127.0.0.1");
      var res = new MockHttpServletResponse();
      filter.doFilter(req, res, new MockFilterChain());
      assertThat(res.getStatus()).isEqualTo(i <= 2 ? 200 : 429);
    }
  }

  @Test
  void untrustedOriginsCannotUseCookieAuthentication() throws Exception {
    var filter =
        new RequestGuardFilter(
            Set.of("http://localhost:5173"), new ObjectMapper().findAndRegisterModules(), 20);
    var req = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
    req.addHeader("Origin", "https://attacker.example");
    var res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    assertThat(res.getStatus()).isEqualTo(403);
  }
}
