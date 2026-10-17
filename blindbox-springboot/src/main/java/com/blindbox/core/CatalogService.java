package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.core.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CatalogService {
    private final PlatformMapper db;
    private final InventoryLottery lottery;
    private final CatalogCache cache;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public CatalogService(PlatformMapper db,InventoryLottery lottery,CatalogCache cache,com.fasterxml.jackson.databind.ObjectMapper json) {
        this.db=db;this.lottery=lottery;this.cache=cache;this.json=json;
    }
    public static int page(int value) {
        if(value<1 || value>100000) throw new BusinessException(400,"页码须在 1 至 100000 之间");
        return value;
    }
    public static int size(int value) {
        if(value<1 || value>100) throw new BusinessException(400,"每页数量须在 1 至 100 之间");
        return value;
    }
    public static Map<String,Object> paged(Object list,long total,int page,int size) {
        return Map.of("list",list,"total",total,"page",page,"pageSize",size,"totalPages",(total+size-1)/size);
    }

    public Map<String,Object> list(int page,int size,String series,String search,boolean admin) {
        page(page);size(size);
        if(search.length()>100 || series.length()>20) throw new BusinessException(400,"查询条件过长");
        if(admin) {
            Map<String,Object> result=new HashMap<>(paged(db.boxes(true,series,search,(page-1)*size,size),
                db.countBoxes(true,series,search),page,size));
            result.put("asOf",java.time.Instant.now().toString());
            return result;
        }
        String suffix=page+":"+size+":"+series+":"+Base64.getUrlEncoder().encodeToString(
            search.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String,Object> metadata=cache.getOrLoad(suffix,()->{
            List<Map<String,Object>> list=db.boxes(false,series,search,(page-1)*size,size).stream()
                .map(this::metadata).toList();
            Map<String,Object> value=new HashMap<>(paged(list,db.countBoxes(false,series,search),page,size));
            value.put("metadataAsOf",java.time.Instant.now().toString());
            return value;
        });
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> items=(List<Map<String,Object>>)metadata.get("list");
        List<Integer> ids=items.stream().map(item->((Number)item.get("id")).intValue()).toList();
        Map<Integer,BoxStock> stocks=new HashMap<>();
        if(!ids.isEmpty()) for(BoxStock row:db.stocks(ids)) stocks.put(row.getId(),row);
        List<Map<String,Object>> current=new ArrayList<>();
        for(Map<String,Object> item:items) {
            BoxStock stock=stocks.get(((Number)item.get("id")).intValue());
            if(stock==null || !"ON_SALE".equals(stock.getStatus())) continue;
            Map<String,Object> view=new LinkedHashMap<>(item);
            view.put("stock",stock.getStock());view.put("status",stock.getStatus());view.put("price",stock.getPrice());current.add(view);
        }
        Map<String,Object> result=new HashMap<>(metadata);
        result.put("list",current);result.put("asOf",java.time.Instant.now().toString());
        return result;
    }

    private Map<String,Object> metadata(Box box) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",box.getId());result.put("name",box.getName());result.put("series",box.getSeries());
        result.put("image",box.getImage());result.put("description",box.getDescription());
        result.put("status",box.getStatus());
        return result;
    }

    /** One MVCC statement reads box and prize rows from the same snapshot, without inventory write locks. */
    public Map<String,Object> detail(int id,boolean admin) {
        List<CatalogRow> rows=db.detailSnapshot(id);
        if(rows.isEmpty() || (!admin && !"ON_SALE".equals(rows.get(0).getStatus())))
            throw new BusinessException(404,"盲盒不存在或已下架");
        CatalogRow first=rows.get(0);
        Box box=new Box();box.setId(first.getId());box.setName(first.getName());box.setSeries(first.getSeries());
        box.setImage(first.getImage());box.setDescription(first.getDescription());box.setPrice(first.getPrice());
        box.setStock(first.getStock());box.setStatus(first.getStatus());
        List<Prize> prizes=new ArrayList<>();
        for(CatalogRow row:rows) {
            if(row.getPrizeId()==null) continue; // A draft box can have no prize rows.
            Prize prize=new Prize();prize.setId(row.getPrizeId());prize.setBlindBoxId(id);
            prize.setPrizeName(row.getPrizeName());prize.setPrizeImg(row.getPrizeImg());prize.setRarity(row.getRarity());
            prize.setStock(row.getPrizeStock());prize.setValue(row.getPrizeValue());prizes.add(prize);
        }
        long total=lottery.total(prizes);
        if(total!=box.getStock()) throw new BusinessException(409,"库存校验失败，请联系管理员");
        List<Map<String,Object>> views=new ArrayList<>();
        Map<String,Long> rarityCounts=new LinkedHashMap<>();
        for(Prize p:prizes) {
            Map<String,Object> view=new LinkedHashMap<>();
            view.put("id",p.getId());view.put("prizeName",p.getPrizeName());view.put("prizeImg",p.getPrizeImg());
            view.put("rarity",p.getRarity());view.put("stock",p.getStock());view.put("value",p.getValue());
            view.put("probability",total==0?0:(double)p.getStock()/total);views.add(view);
            rarityCounts.merge(p.getRarity(),p.getStock().longValue(),Long::sum);
        }
        return Map.of("box",box,"prizes",views,"totalStock",total,"rarityCounts",rarityCounts,"asOf",java.time.Instant.now().toString());
    }
    @Transactional(timeout=10)
    public Box create(int actor,CreateBox request,String key) {
        DrawService.requestKey(key);
        if(db.lockUser(actor)==null) throw new BusinessException(401,"用户不存在");
        String hash;
        try {
            hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(request)));
        } catch(Exception e) {throw new IllegalStateException(e);}
        Box prior=db.createdByRequest(actor,key);
        if(prior!=null) {
            if(!hash.equals(prior.getCreatePayloadHash())) throw new BusinessException(409,"该请求 ID 对应的商品配置不同");
            return prior;
        }
        long total=request.prizes().stream().mapToLong(PrizeInput::stock).sum();
        if(total>10000000) throw new BusinessException(400,"奖池库存不能超过一千万");
        Box box=new Box();box.setName(request.name().trim());box.setSeries(request.series());
        box.setImage(request.image());box.setDescription(request.description()==null?"":request.description());
        box.setPrice(request.price());box.setStock((int)total);box.setStatus("DRAFT");
        box.setCreatorId(actor);box.setCreateRequestId(key);box.setCreatePayloadHash(hash);
        DrawService.requireOne(db.insertBox(box),"创建商品失败");
        String requestId=UUID.randomUUID().toString();
        for(PrizeInput input:request.prizes()) {
            Prize p=new Prize();p.setBlindBoxId(box.getId());p.setPrizeName(input.prizeName().trim());
            p.setPrizeImg(input.prizeImg());p.setRarity(input.rarity());p.setStock(input.stock());p.setValue(input.value());
            DrawService.requireOne(db.insertPrize(p),"创建奖品失败");
            DrawService.requireOne(db.stock(box.getId(),p.getId(),actor,input.stock(),input.stock(),"INITIAL",null,requestId),"初始库存流水失败");
        }
        cache.invalidateAfterCommit();return box;
    }
    @Transactional(timeout=5)
    public void update(int id,UpdateBox request) {
        Box box=db.lockBox(id);
        if(box==null) throw new BusinessException(404,"盲盒不存在");
        if("ARCHIVED".equals(box.getStatus())) throw new BusinessException(409,"已归档商品不可修改");
        DrawService.requireOne(db.updateBox(id,request),"商品更新失败");
        cache.invalidateAfterCommit();
    }
    @Transactional(timeout=10)
    public void status(int id,String status) {
        Box box=db.lockBox(id);
        if(box==null) throw new BusinessException(404,"盲盒不存在");
        if("ARCHIVED".equals(box.getStatus())) throw new BusinessException(409,"已归档商品不能重新上架");
        if("ON_SALE".equals(status)) {
            long total=lottery.total(db.lockPrizes(id));
            if(total==0 || total!=box.getStock()) throw new BusinessException(409,"请先完成配货并校验库存");
        }
        DrawService.requireOne(db.status(id,status),"修改状态失败");cache.invalidateAfterCommit();
    }
    @Transactional(timeout=10)
    public void restock(int actor,int id,Restock request,String key) {
        DrawService.requestKey(key);
        if(db.lockUser(actor)==null) throw new BusinessException(401,"用户不存在");
        Box box=db.lockBox(id);
        if(box==null) throw new BusinessException(404,"盲盒不存在");
        StockReceipt prior=db.restockByRequest(id,key);
        if(prior!=null) {
            if(!prior.getPrizeId().equals(request.prizeId()) || !prior.getDelta().equals(request.quantity())) throw new BusinessException(409,"请求 ID 对应的补货数量不同");
            return;
        }
        if("ARCHIVED".equals(box.getStatus())) throw new BusinessException(409,"已归档商品不能补货");
        Prize selected=db.lockPrizes(id).stream().filter(p->p.getId().equals(request.prizeId())).findFirst()
            .orElseThrow(()->new BusinessException(404,"奖品不属于该盲盒"));
        DrawService.requireOne(db.addPrize(selected.getId(),request.quantity()),"奖品库存超过上限");
        DrawService.requireOne(db.addBox(id,request.quantity()),"奖池库存超过上限");
        DrawService.requireOne(db.stock(id,selected.getId(),actor,request.quantity(),selected.getStock()+request.quantity(),"RESTOCK",null,key),"补货流水失败");
    }
}
