package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.core.Models.Prize;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.List;

@Component
public class InventoryLottery {
    private final SecureRandom random = new SecureRandom();
    public Prize draw(List<Prize> prizes) {
        long total=total(prizes);
        if(total<=0) throw new BusinessException(409,"奖池已售罄");
        return select(prizes,random.nextLong(total));
    }
    public long total(List<Prize> prizes) {
        return prizes.stream().mapToLong(p -> {
            if(p.getStock()==null || p.getStock()<0) throw new BusinessException(409,"奖池库存异常");
            return p.getStock();
        }).sum();
    }
    public Prize select(List<Prize> prizes,long ticket) {
        long total=total(prizes);
        if(ticket<0 || ticket>=total) throw new IllegalArgumentException("票号超出库存范围");
        long cumulative=0;
        for(Prize prize:prizes) {
            cumulative+=prize.getStock();
            if(ticket<cumulative) return prize;
        }
        throw new IllegalStateException("库存区间不完整");
    }
}
