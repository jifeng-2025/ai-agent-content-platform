package com.yupi.template.runtime;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class A3BudgetTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);final RuntimeStore store=mock(RuntimeStore.class);final RuntimeConfig config=mock(RuntimeConfig.class);
 final RuntimeScope.Ticket ticket=new RuntimeScope.Ticket("task","request","run",1);
 final Map<String,Object> budget=new HashMap<>();
 @BeforeEach void setup(){budget.putAll(Map.of("callsUsed",0,"maxCalls",12,"reservedCostMicros",0,"maxEstimatedCostMicros",1000,"imageRetriesUsed",0,"maxImageRetries",3,"remainingMs",900000));when(store.runtime("task")).thenReturn(budget);when(config.getTextReserveMicros()).thenReturn(100L);when(config.getImageReserveMicros()).thenReturn(200L);}
 @Test void estimatedCostCapStopsBeforeAnotherRequestIsRecorded(){budget.put("reservedCostMicros",950);when(jdbc.queryForObject(anyString(),eq(String.class),eq("task"),eq("request"))).thenReturn("CREATE");assertEquals("BUDGET_EXHAUSTED",assertThrows(RuntimeStop.class,()->new RuntimeLedger(jdbc,store,config).prepare(ticket,"BODY","dashscope","payload")).status);verify(store,never()).event(anyString(),anyString(),any());}
 @Test void explicitImageRetryCannotExceedImageCap(){budget.put("imageRetriesUsed",3);when(jdbc.queryForObject(anyString(),eq(String.class),eq("task"),eq("request"))).thenReturn("RETRY_IMAGE");assertEquals("BUDGET_EXHAUSTED",assertThrows(RuntimeStop.class,()->new RuntimeLedger(jdbc,store,config).prepare(ticket,"IMAGE:image-1","image:PEXELS","payload")).status);verify(store,never()).event(anyString(),anyString(),any());}
 @Test void humanAuthorizedUncertainImageStillConsumesImageRetryBudget(){budget.put("imageRetriesUsed",3);when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("status","AUTHORIZED_RETRY")));when(jdbc.queryForObject(anyString(),eq(String.class),eq("task"),eq("request"))).thenReturn("ACCEPT");assertEquals("BUDGET_EXHAUSTED",assertThrows(RuntimeStop.class,()->new RuntimeLedger(jdbc,store,config).prepare(ticket,"IMAGE:image-1","image:PEXELS","payload")).status);verify(store,never()).event(anyString(),anyString(),any());}
}
