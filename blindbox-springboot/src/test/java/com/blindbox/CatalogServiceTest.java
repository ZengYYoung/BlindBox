package com.blindbox;
import com.blindbox.core.*;
import com.blindbox.core.Models.*;
import com.blindbox.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class CatalogServiceTest {
    @Test void zeroStockPoolCannotBePutOnSale() {
        PlatformMapper db=mock(PlatformMapper.class);Box box=new Box();box.setStock(0);box.setStatus("DRAFT");
        when(db.lockBox(1)).thenReturn(box);when(db.lockPrizes(1)).thenReturn(List.of());
        CatalogService service=new CatalogService(db,new InventoryLottery(),mock(CatalogCache.class),new ObjectMapper());
        assertThrows(BusinessException.class,()->service.status(1,"ON_SALE"));verify(db,never()).status(anyInt(),anyString());
    }
    @Test void restockReplayAndChangedPrizeAreCheckedBeforeMutation() {
        PlatformMapper db=mock(PlatformMapper.class);Box box=new Box();box.setStatus("DRAFT");
        when(db.lockUser(7)).thenReturn(new User());when(db.lockBox(1)).thenReturn(box);
        StockReceipt receipt=new StockReceipt();receipt.setPrizeId(11);receipt.setDelta(20);
        when(db.restockByRequest(1,"request-001")).thenReturn(receipt);
        CatalogService service=new CatalogService(db,new InventoryLottery(),mock(CatalogCache.class),new ObjectMapper());
        service.restock(7,1,new Restock(11,20),"request-001");
        var sequence=inOrder(db);sequence.verify(db).lockUser(7);sequence.verify(db).lockBox(1);sequence.verify(db).restockByRequest(1,"request-001");
        assertThrows(BusinessException.class,()->service.restock(7,1,new Restock(12,20),"request-001"));
        verify(db,never()).addPrize(anyInt(),anyInt());
    }
    @Test void sameCreationKeyAndPayloadReturnOriginalBox() throws Exception {
        PlatformMapper db=mock(PlatformMapper.class);ObjectMapper json=new ObjectMapper();
        CreateBox input=new CreateBox("海洋","sea","/images/sea.png","",5900L,List.of(new PrizeInput("海豚","/images/dolphin.png","common",30,0)));
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));
        Box prior=new Box();prior.setId(1);prior.setCreatePayloadHash(hash);
        when(db.lockUser(7)).thenReturn(new User());when(db.createdByRequest(7,"request-001")).thenReturn(prior);
        CatalogService service=new CatalogService(db,new InventoryLottery(),mock(CatalogCache.class),json);
        assertSame(prior,service.create(7,input,"request-001"));
        var sequence=inOrder(db);sequence.verify(db).lockUser(7);sequence.verify(db).createdByRequest(7,"request-001");
        CreateBox changed=new CreateBox("另一个","sea","/images/sea.png","",5900L,input.prizes());
        assertThrows(BusinessException.class,()->service.create(7,changed,"request-001"));
        verify(db,never()).insertBox(any());
    }
}
