package com.yupi.template.runtime;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class A3ExternalTest {
 static final String ID=RuntimeExternal.hash("fixed-provider-request");
 final RuntimeScope.Ticket ticket=new RuntimeScope.Ticket("task","request","run",1);
 final RuntimeLedger ledger=mock(RuntimeLedger.class);final RuntimeStore store=mock(RuntimeStore.class);
 RuntimeLedger.Call call(boolean fresh,String status){return new RuntimeLedger.Call(ID,fresh,status,"saved",null,System.currentTimeMillis()+5000);}
 @Test void storedResultNeverCallsProvider()throws Exception {when(ledger.prepare(any(),anyString(),anyString(),anyString())).thenReturn(call(false,"SUCCEEDED"));Callable<String> action=mock(Callable.class);assertEquals("saved",new RuntimeExternal(ledger,store,List.of()).call(ticket,"BODY","dashscope","payload",action));verifyNoInteractions(action);}
 @Test void unknownSynchronousRequestStopsWithoutResubmission()throws Exception {when(ledger.prepare(any(),anyString(),anyString(),anyString())).thenReturn(call(false,"IN_FLIGHT"));Callable<String> action=mock(Callable.class);assertEquals("EXTERNAL_UNCERTAIN",assertThrows(RuntimeStop.class,()->new RuntimeExternal(ledger,store,List.of()).call(ticket,"BODY","dashscope","payload",action)).status);verifyNoInteractions(action);}
 @Test void supportedProviderIsQueriedBeforeAnyResubmission()throws Exception {when(ledger.prepare(any(),anyString(),anyString(),anyString())).thenReturn(call(false,"IN_FLIGHT"));when(ledger.queryDeadline(any(),anyString())).thenReturn(System.currentTimeMillis()+5000);var provider=mock(RuntimeProvider.class);when(provider.supports("queryable")).thenReturn(true);when(provider.query(ID,null)).thenReturn(new RuntimeProvider.Result("SUCCEEDED","job-1","found",null));assertEquals("found",new RuntimeExternal(ledger,store,List.of(provider)).call(ticket,"BODY","queryable","payload",()->{throw new AssertionError("Duplicate effect");}));verify(provider,never()).submit(anyString(),anyString());}
 @Test void expiredStepPausesAndMarksUncertain(){var invoked=new java.util.concurrent.atomic.AtomicInteger();when(ledger.prepare(any(),anyString(),anyString(),anyString())).thenReturn(new RuntimeLedger.Call(ID,true,"IN_FLIGHT",null,null,0));assertEquals("EXTERNAL_UNCERTAIN",assertThrows(RuntimeStop.class,()->new RuntimeExternal(ledger,store,List.of()).call(ticket,"BODY","dashscope","payload",()->{invoked.incrementAndGet();return "late";})).status);assertEquals(0,invoked.get());verify(ledger).uncertain(ticket,ID);verify(ledger,never()).save(any(),anyString(),any());}
 @Test void lostLeaseCannotSaveLateResult(){when(ledger.prepare(any(),anyString(),anyString(),anyString())).thenReturn(call(true,"IN_FLIGHT"));doThrow(new RuntimeStop("FENCED")).when(store).guard(ticket);assertEquals("FENCED",assertThrows(RuntimeStop.class,()->new RuntimeExternal(ledger,store,List.of()).call(ticket,"BODY","dashscope","payload",()->"late")).status);verify(ledger,never()).save(any(),anyString(),any());}
 @Test void modelExceptionIsNotBlindlyRetried(){when(ledger.prepare(any(),anyString(),anyString(),anyString())).thenReturn(call(true,"IN_FLIGHT"));var count=new java.util.concurrent.atomic.AtomicInteger();assertEquals("EXTERNAL_UNCERTAIN",assertThrows(RuntimeStop.class,()->new RuntimeExternal(ledger,store,List.of()).call(ticket,"BODY","dashscope","payload",()->{count.incrementAndGet();throw new IllegalStateException("connection lost");})).status);assertEquals(1,count.get());verify(ledger).uncertain(ticket,ID);}
}
