package com.blindbox;
import com.blindbox.core.*;
import com.blindbox.core.Models.*;
import com.blindbox.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawTransactionServiceTest {
    PlatformMapper db;InventoryLottery lottery;DrawTransactionService service;
    User user;Box box;Prize prize;
    @BeforeEach void setup() {
        db=mock(PlatformMapper.class);lottery=mock(InventoryLottery.class);
        service=new DrawTransactionService(db,lottery);
        user=new User();user.setId(7);user.setBalance(10000L);
        box=new Box();box.setId(1);box.setName("海洋");box.setPrice(5900L);box.setStock(3);box.setStatus("ON_SALE");
        prize=new Prize();prize.setId(11);prize.setStock(3);prize.setPrizeName("海豚");prize.setPrizeImg("/images/dolphin.png");prize.setRarity("common");
        when(db.lockUser(7)).thenReturn(user);
    }
    void pool() {
        when(db.lockBox(1)).thenReturn(box);when(db.lockPrizes(1)).thenReturn(List.of(prize));
        when(lottery.total(List.of(prize))).thenReturn(3L);when(lottery.draw(List.of(prize))).thenReturn(prize);
    }
    void successWrites() {
        when(db.debit(7,5900)).thenReturn(1);when(db.takePrize(11)).thenReturn(1);when(db.takeBox(1)).thenReturn(1);
        when(db.insertOrder(any())).thenAnswer(inv->{Order o=inv.getArgument(0);o.setId(23);return 1;});
        when(db.wallet(7,"request-001","DRAW",-5900,4100,23)).thenReturn(1);
        when(db.stock(1,11,7,-1,2,"DRAW",23,"request-001")).thenReturn(1);
    }
    @Test void successfulDrawWritesAllRecordsInLockOrder() {
        pool();successWrites();Order result=service.draw(7,1,"request-001").order();
        assertEquals(23,result.getId());assertEquals("海豚",result.getPrizeName());assertEquals(5900,result.getPrice());
        InOrder sequence=inOrder(db);
        sequence.verify(db).lockUser(7);sequence.verify(db).findOrderByRequest(7,"request-001");
        sequence.verify(db).lockBox(1);sequence.verify(db).lockPrizes(1);sequence.verify(db).debit(7,5900);

    }
    @Test void replayReturnsOriginalOrderWithoutDebit() {
        Order prior=new Order();prior.setId(23);prior.setBlindBoxId(1);
        when(db.findOrderByRequest(7,"request-001")).thenReturn(prior);
        assertSame(prior,service.draw(7,1,"request-001").order());verify(db,never()).lockBox(anyInt());verify(db,never()).debit(anyInt(),anyLong());
    }
    @Test void sameKeyDifferentBoxRejects() {
        Order prior=new Order();prior.setBlindBoxId(2);when(db.findOrderByRequest(7,"request-001")).thenReturn(prior);
        assertThrows(BusinessException.class,()->service.draw(7,1,"request-001"));
    }
    @Test void failedConditionalDebitStopsOrderCreation() {
        pool();when(db.debit(7,5900)).thenReturn(0);
        assertThrows(BusinessException.class,()->service.draw(7,1,"request-001"));
        verify(db,never()).takePrize(anyInt());verify(db,never()).insertOrder(any());
    }
    @Test void insufficientBalanceStopsWrites() {
        pool();user.setBalance(100L);
        assertThrows(BusinessException.class,()->service.draw(7,1,"request-001"));verify(db,never()).debit(anyInt(),anyLong());
    }
    @Test void divergentPoolStopsWrites() {
        pool();box.setStock(4);
        assertThrows(BusinessException.class,()->service.draw(7,1,"request-001"));verify(db,never()).debit(anyInt(),anyLong());
    }
    @Test void orderFailureStopsLedgerWrites() {
        pool();successWrites();doReturn(0).when(db).insertOrder(any());
        assertThrows(BusinessException.class,()->service.draw(7,1,"request-001"));
        verify(db,never()).wallet(anyInt(),anyString(),anyString(),anyLong(),anyLong(),any());
        verify(db,never()).stock(anyInt(),anyInt(),anyInt(),anyInt(),anyInt(),anyString(),any(),anyString());
    }
    @Test void invalidKeysRejectBeforeQueries() {
        assertThrows(BusinessException.class,()->DrawService.requestKey(null));
        assertThrows(BusinessException.class,()->DrawService.requestKey("tiny"));
        assertThrows(BusinessException.class,()->DrawService.requestKey("invalid space"));
        assertEquals("request-001",DrawService.requestKey("request-001"));
    }
}
