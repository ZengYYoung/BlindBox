package com.blindbox;
import com.blindbox.core.*;
import com.blindbox.core.Models.UpdateBox;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class MapperBindingTest {
    @Test void snapshotAndResultLookupAreNonlockingAndBatchStocksBindIds() {
        Configuration config=new Configuration();config.addMapper(PlatformMapper.class);
        String prefix=PlatformMapper.class.getName()+".";
        assertFalse(config.getMappedStatement(prefix+"detailSnapshot").getBoundSql(1).getSql().toUpperCase().contains("FOR UPDATE"));
        assertFalse(config.getMappedStatement(prefix+"findOrderByRequest").getBoundSql(java.util.Map.of("user",1,"request","request-001")).getSql().toUpperCase().contains("FOR UPDATE"));
        var bound=config.getMappedStatement(prefix+"stocks").getBoundSql(java.util.Map.of("ids",java.util.List.of(1,2))).getSql();
        assertTrue(bound.contains("IN"));assertEquals(2,bound.chars().filter(c->c=='?').count());
    }
    @Test void parentGuardedIdempotencyLookupsDoNotLockMissingIndexGaps() {
        Configuration config=new Configuration();config.addMapper(PlatformMapper.class);
        String prefix=PlatformMapper.class.getName()+".";
        for(String query:java.util.List.of("findOrderByRequest","createdByRequest","rechargeByRequest","restockByRequest")) {
            String sql=config.getMappedStatement(prefix+query)
                .getBoundSql(java.util.Map.of("user",1,"box",1,"request","request-001","key","request-001")).getSql();
            assertFalse(sql.toUpperCase().contains("FOR UPDATE"),query);
        }
        assertTrue(config.getMappedStatement(prefix+"lockUser").getBoundSql(1).getSql().contains("FOR UPDATE"));
        assertTrue(config.getMappedStatement(prefix+"lockBox").getBoundSql(1).getSql().contains("FOR UPDATE"));
        assertTrue(config.getMappedStatement(prefix+"lockPrizes").getBoundSql(1).getSql().contains("FOR UPDATE"));
    }
    @Test void annotationStatementsAndRecordPropertyBindingsAreValid() {
        Configuration config=new Configuration();config.addMapper(PlatformMapper.class);
        UpdateBox dto=new UpdateBox("海洋","sea","/images/sea.png","介绍",5900L);
        Map<String,Object> parameters=Map.of("id",1,"request",dto);
        var bound=config.getMappedStatement(PlatformMapper.class.getName()+".updateBox").getBoundSql(parameters);
        var meta=SystemMetaObject.forObject(parameters);
        assertEquals(5900L,meta.getValue("request.price"));
        for(var parameter:bound.getParameterMappings()) assertNotNull(meta.getValue(parameter.getProperty()));
        assertFalse(bound.getSql().contains("#"));
    }
}
