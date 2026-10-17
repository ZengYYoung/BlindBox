package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.core.Models.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class DrawTransactionService {
    public record DrawResult(Order order, boolean replayed) {}
    private final PlatformMapper db;
    private final InventoryLottery lottery;
    public DrawTransactionService(PlatformMapper db,InventoryLottery lottery) {
        this.db=db;this.lottery=lottery;
    }
    @Transactional(timeout=10)
    public DrawResult draw(int userId,int boxId,String request) {
        DrawService.requestKey(request);
        User user=db.lockUser(userId);
        if(user==null) throw new BusinessException(401,"用户不存在");
        // First consistent read occurs AFTER the user lock: prior same-user commits are visible.
        // The parent lock serializes idempotency; a missing-row FOR UPDATE would add gap locks.
        Order prior=db.findOrderByRequest(userId,request);
        if(prior!=null) {
            if(prior.getBlindBoxId()!=boxId) throw new BusinessException(409,"该请求 ID 已用于其他盲盒");
            return new DrawResult(prior, true);
        }
        Box box=db.lockBox(boxId);
        if(box==null) throw new BusinessException(404,"盲盒不存在");
        if(!"ON_SALE".equals(box.getStatus())) throw new BusinessException(409,"盲盒未上架");
        List<Prize> prizes=db.lockPrizes(boxId);
        long total=lottery.total(prizes);
        if(total!=box.getStock()) throw new BusinessException(409,"库存校验失败，请联系管理员");
        if(total==0) throw new BusinessException(409,"库存已售罄");
        if(user.getBalance()<box.getPrice()) throw new BusinessException(409,"余额不足");
        Prize selected=lottery.draw(prizes);
        requireOne(db.debit(userId,box.getPrice()),"余额扣减失败");
        requireOne(db.takePrize(selected.getId()),"奖品库存扣减失败");
        requireOne(db.takeBox(boxId),"盲盒库存扣减失败");
        Order order=new Order();
        order.setUserId(userId);order.setBlindBoxId(boxId);order.setPrizeId(selected.getId());
        order.setRequestId(request);order.setPrice(box.getPrice());order.setStatus("已支付");
        order.setBlindBoxName(box.getName());order.setPrizeName(selected.getPrizeName());
        order.setPrizeImg(selected.getPrizeImg());order.setRarity(selected.getRarity());
        requireOne(db.insertOrder(order),"订单创建失败");
        requireOne(db.wallet(userId,request,"DRAW",-box.getPrice(),user.getBalance()-box.getPrice(),order.getId()),"资金流水失败");
        requireOne(db.stock(boxId,selected.getId(),userId,-1,selected.getStock()-1,"DRAW",order.getId(),request),"库存流水失败");
        return new DrawResult(order, false);
    }
    public static void requireOne(int rows,String message) {
        if(rows!=1) throw new BusinessException(409,message);
    }
}
