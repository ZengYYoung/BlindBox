package com.blindbox;

import com.blindbox.core.*;
import com.blindbox.core.Models.*;
import com.blindbox.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogConcurrencyTest {
    private CatalogRow row(int prize,int quantity,String rarity) {
        CatalogRow row=new CatalogRow();
        row.setId(1);row.setName("海洋");row.setSeries("sea");row.setImage("/images/sea.png");row.setDescription("");
        row.setPrice(5900L);row.setStock(3);row.setStatus("ON_SALE");
        row.setPrizeId(prize);row.setPrizeName("奖品"+prize);row.setPrizeImg("/images/dolphin.png");
        row.setRarity(rarity);row.setPrizeStock(quantity);row.setPrizeValue(0);return row;
    }
    @Test void detailUsesOneNonlockingSnapshotAndKeepsInventoryProbabilities() {
        PlatformMapper db=mock(PlatformMapper.class);
        when(db.detailSnapshot(1)).thenReturn(List.of(row(11,2,"common"),row(12,1,"secret")));
        CatalogService service=new CatalogService(db,new InventoryLottery(),mock(CatalogCache.class),new ObjectMapper());
        Map<String,Object> result=service.detail(1,false);
        assertEquals(3L,result.get("totalStock"));
        @SuppressWarnings("unchecked") var prizes=(List<Map<String,Object>>)result.get("prizes");
        assertEquals(2.0/3,((Number)prizes.get(0).get("probability")).doubleValue(),1e-9);
        verify(db).detailSnapshot(1);
        verify(db,never()).lockBox(anyInt());verify(db,never()).lockPrizes(anyInt());
        verify(db,never()).box(anyInt());verify(db,never()).prizes(anyInt());
    }
    @Test void emptyDraftIsOnlyVisibleToAdminAndInconsistentSnapshotIsRejected() {
        PlatformMapper db=mock(PlatformMapper.class);CatalogRow draft=row(11,0,"common");
        draft.setPrizeId(null);draft.setStock(0);draft.setStatus("DRAFT");
        when(db.detailSnapshot(1)).thenReturn(List.of(draft));
        CatalogService service=new CatalogService(db,new InventoryLottery(),mock(CatalogCache.class),new ObjectMapper());
        assertThrows(BusinessException.class,()->service.detail(1,false));
        assertEquals(0L,service.detail(1,true).get("totalStock"));
        when(db.detailSnapshot(1)).thenReturn(List.of(row(11,2,"common")));
        assertThrows(BusinessException.class,()->service.detail(1,false));
    }
    @Test void cachedMetadataGetsFreshStockPriceAndVisibilityWithoutMutation() {
        PlatformMapper db=mock(PlatformMapper.class);CatalogCache cache=mock(CatalogCache.class);
        Map<String,Object> item=new HashMap<>(Map.of("id",1,"name","海洋"));
        Map<String,Object> metadata=new HashMap<>(Map.of("list",List.of(item),"total",1,"page",1,"pageSize",10,"totalPages",1,"metadataAsOf","snapshot"));
        when(cache.getOrLoad(anyString(),any())).thenReturn(metadata);
        BoxStock current=new BoxStock();current.setId(1);current.setStock(99);current.setStatus("ON_SALE");current.setPrice(7900L);
        when(db.stocks(List.of(1))).thenReturn(List.of(current));
        CatalogService service=new CatalogService(db,new InventoryLottery(),cache,new ObjectMapper());
        @SuppressWarnings("unchecked") var list=(List<Map<String,Object>>)service.list(1,10,"","",false).get("list");
        assertEquals(99,list.get(0).get("stock"));assertEquals(7900L,list.get(0).get("price"));
        assertFalse(item.containsKey("stock"));assertFalse(item.containsKey("price"));
        current.setStatus("OFF_SALE");
        assertEquals(List.of(),service.list(1,10,"","",false).get("list"));
        verify(db,never()).boxes(anyBoolean(),anyString(),anyString(),anyInt(),anyInt());
    }
    @Test void successfulRestockDoesNotInvalidateStaticMetadata() {
        PlatformMapper db=mock(PlatformMapper.class);CatalogCache cache=mock(CatalogCache.class);
        Box box=new Box();box.setStatus("ON_SALE");Prize prize=new Prize();prize.setId(11);prize.setStock(2);
        when(db.lockUser(7)).thenReturn(new User());when(db.lockBox(1)).thenReturn(box);
        when(db.lockPrizes(1)).thenReturn(List.of(prize));when(db.addPrize(11,10)).thenReturn(1);when(db.addBox(1,10)).thenReturn(1);
        when(db.stock(1,11,7,10,12,"RESTOCK",null,"request-001")).thenReturn(1);
        CatalogService service=new CatalogService(db,new InventoryLottery(),cache,new ObjectMapper());
        service.restock(7,1,new Restock(11,10),"request-001");
        verifyNoInteractions(cache);
    }
}
