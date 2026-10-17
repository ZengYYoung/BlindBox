package com.blindbox;
import com.blindbox.core.*;
import com.blindbox.core.Models.Prize;
import com.blindbox.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryLotteryTest {
    private final InventoryLottery lottery=new InventoryLottery();
    private Prize prize(int id,int stock) {Prize p=new Prize();p.setId(id);p.setStock(stock);return p;}
    @Test void everyPhysicalItemHasOneTicket() {
        List<Prize> pool=List.of(prize(1,3),prize(2,2),prize(3,1));
        Map<Integer,Integer> counts=new HashMap<>();
        for(long ticket=0;ticket<6;ticket++) counts.merge(lottery.select(pool,ticket).getId(),1,Integer::sum);
        assertEquals(Map.of(1,3,2,2,3,1),counts);
    }
    @Test void zeroStockNeverWins() {
        List<Prize> pool=List.of(prize(1,0),prize(2,2),prize(3,0));
        assertEquals(2,lottery.select(pool,0).getId());assertEquals(2,lottery.select(pool,1).getId());
    }
    @Test void soldOutAndInvalidTicketReject() {
        assertThrows(BusinessException.class,()->lottery.draw(List.of()));
        assertThrows(BusinessException.class,()->lottery.draw(List.of(prize(1,0))));
        assertThrows(IllegalArgumentException.class,()->lottery.select(List.of(prize(1,1)),1));
        assertThrows(IllegalArgumentException.class,()->lottery.select(List.of(prize(1,1)),-1));
        assertThrows(BusinessException.class,()->lottery.total(List.of(prize(1,-1))));
    }
    @Test void noReplacementConsumesExactInitialComposition() {
        List<Prize> pool=new ArrayList<>(List.of(prize(1,30),prize(2,20),prize(3,5)));
        Map<Integer,Integer> drawn=new HashMap<>();
        while(lottery.total(pool)>0) {
            Prize p=lottery.draw(pool);drawn.merge(p.getId(),1,Integer::sum);p.setStock(p.getStock()-1);
        }
        assertEquals(Map.of(1,30,2,20,3,5),drawn);
    }
    @Test void probabilitiesChangeAfterConsumption() {
        List<Prize> pool=List.of(prize(1,9),prize(2,1));
        assertEquals(10,lottery.total(pool));pool.get(1).setStock(0);
        assertEquals(9,lottery.total(pool));
        for(int i=0;i<9;i++) assertEquals(1,lottery.select(pool,i).getId());
    }
}
