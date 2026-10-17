package com.blindbox.core;

import com.blindbox.core.Models.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface PlatformMapper {
    @Select("SELECT * FROM user WHERE id=#{id}") User user(int id);
    @Select("SELECT * FROM user WHERE id=#{id} FOR UPDATE") User lockUser(int id);
    @Select("SELECT * FROM user WHERE account=#{account}") User userByAccount(String account);
    @Insert("INSERT INTO user(account,password) VALUES(#{account},#{password})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertUser(User user);
    @Update("UPDATE user SET balance=balance-#{amount} WHERE id=#{id} AND balance>=#{amount}")
    int debit(@Param("id") int id,@Param("amount") long amount);
    @Update("UPDATE user SET balance=balance+#{amount} WHERE id=#{id} AND balance<=1000000000000-#{amount}")
    int credit(@Param("id") int id,@Param("amount") long amount);
    @Select("SELECT * FROM blind_box WHERE id=#{id}") Box box(int id);
    @Select("SELECT b.id,b.name,b.series,b.image,b.description,b.price,b.stock,b.status,"
        + "p.id AS prize_id,p.prize_name,p.prize_img,p.rarity,p.stock AS prize_stock,p.value AS prize_value "
        + "FROM blind_box b LEFT JOIN prize p ON p.blind_box_id=b.id WHERE b.id=#{id} ORDER BY p.id")
    List<CatalogRow> detailSnapshot(int id);
    @Select({"<script>SELECT id,stock,status,price FROM blind_box WHERE id IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<BoxStock> stocks(@Param("ids") List<Integer> ids);
    @Select("SELECT * FROM blind_box WHERE id=#{id} FOR UPDATE") Box lockBox(int id);
    @Select("SELECT * FROM prize WHERE blind_box_id=#{id} ORDER BY id") List<Prize> prizes(int id);
    @Select("SELECT * FROM prize WHERE blind_box_id=#{id} ORDER BY id FOR UPDATE") List<Prize> lockPrizes(int id);
    @Select("SELECT * FROM blind_box WHERE (#{admin}=true OR status='ON_SALE') AND (#{series}='' OR series=#{series}) AND name LIKE CONCAT('%',#{search},'%') ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Box> boxes(@Param("admin") boolean admin,@Param("series") String series,@Param("search") String search,@Param("offset") int offset,@Param("size") int size);
    @Select("SELECT COUNT(*) FROM blind_box WHERE (#{admin}=true OR status='ON_SALE') AND (#{series}='' OR series=#{series}) AND name LIKE CONCAT('%',#{search},'%')")
    long countBoxes(@Param("admin") boolean admin,@Param("series") String series,@Param("search") String search);
    @Update("UPDATE prize SET stock=stock-1 WHERE id=#{id} AND stock>0") int takePrize(int id);
    @Update("UPDATE blind_box SET stock=stock-1,version=version+1 WHERE id=#{id} AND stock>0 AND status='ON_SALE'") int takeBox(int id);
    @Insert("INSERT INTO blind_box(name,series,image,description,price,stock,status,creator_id,create_request_id,create_payload_hash) VALUES(#{name},#{series},#{image},#{description},#{price},#{stock},'DRAFT',#{creatorId},#{createRequestId},#{createPayloadHash})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertBox(Box box);
    @Insert("INSERT INTO prize(blind_box_id,prize_name,prize_img,rarity,value,stock) VALUES(#{blindBoxId},#{prizeName},#{prizeImg},#{rarity},#{value},#{stock})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertPrize(Prize prize);
    @Select("SELECT * FROM blind_box WHERE creator_id=#{user} AND create_request_id=#{key}")
    Box createdByRequest(@Param("user") int user,@Param("key") String key);
    @Update("UPDATE blind_box SET name=#{request.name},series=#{request.series},image=#{request.image},description=#{request.description},price=#{request.price},version=version+1 WHERE id=#{id}")
    int updateBox(@Param("id") int id,@Param("request") UpdateBox request);
    @Update("UPDATE blind_box SET status=#{status} WHERE id=#{id}") int status(@Param("id") int id,@Param("status") String status);
    @Update("UPDATE prize SET stock=stock+#{n} WHERE id=#{id} AND stock<=10000000-#{n}")
    int addPrize(@Param("id") int id,@Param("n") int n);
    @Update("UPDATE blind_box SET stock=stock+#{n},version=version+1 WHERE id=#{id} AND stock<=10000000-#{n}")
    int addBox(@Param("id") int id,@Param("n") int n);
    @Select("SELECT * FROM orders WHERE user_id=#{user} AND request_id=#{request}")
    Order findOrderByRequest(@Param("user") int user,@Param("request") String request);
    @Select("SELECT * FROM orders WHERE user_id=#{user} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Order> orders(@Param("user") int user,@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM orders WHERE user_id=#{user}") long countOrders(int user);
    @Insert("INSERT INTO orders(user_id,blind_box_id,prize_id,request_id,price,status,blind_box_name,prize_name,prize_img,rarity) VALUES(#{userId},#{blindBoxId},#{prizeId},#{requestId},#{price},#{status},#{blindBoxName},#{prizeName},#{prizeImg},#{rarity})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertOrder(Order order);
    @Insert("INSERT INTO wallet_ledger(user_id,request_id,type,amount,balance_after,order_id) VALUES(#{user},#{request},#{type},#{amount},#{after},#{order})")
    int wallet(@Param("user") int user,@Param("request") String request,@Param("type") String type,
               @Param("amount") long amount,@Param("after") long after,@Param("order") Integer order);
    @Select("SELECT * FROM wallet_ledger WHERE user_id=#{user} AND type='RECHARGE' AND request_id=#{request}")
    Wallet rechargeByRequest(@Param("user") int user,@Param("request") String request);
    @Insert("INSERT INTO stock_ledger(blind_box_id,prize_id,actor_id,delta,stock_after,reason,order_id,request_id) VALUES(#{box},#{prize},#{actor},#{delta},#{after},#{reason},#{order},#{request})")
    int stock(@Param("box") int box,@Param("prize") int prize,@Param("actor") int actor,@Param("delta") int delta,
        @Param("after") int after,@Param("reason") String reason,@Param("order") Integer order,@Param("request") String request);
    @Select("SELECT prize_id,delta FROM stock_ledger WHERE blind_box_id=#{box} AND reason='RESTOCK' AND request_id=#{request}")
    StockReceipt restockByRequest(@Param("box") int box,@Param("request") String request);
    @Select("SELECT COUNT(*) AS totalItems,COUNT(DISTINCT prize_id) AS uniqueItems,COALESCE(SUM(price),0) AS totalSpent FROM orders WHERE user_id=#{user}")
    Map<String,Object> collectionStats(int user);
    @Select("SELECT prize_id AS prizeId,MAX(prize_name) AS prizeName,MAX(prize_img) AS prizeImg,MAX(rarity) AS rarity,COUNT(*) AS quantity FROM orders WHERE user_id=#{user} GROUP BY prize_id ORDER BY MAX(id) DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> collection(@Param("user") int user,@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT p.*,u.account AS username,EXISTS(SELECT 1 FROM player_show_like l WHERE l.player_show_id=p.id AND l.user_id=#{user}) AS liked FROM player_show p JOIN user u ON u.id=p.user_id ORDER BY p.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Show> shows(@Param("user") int user,@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM player_show") long countShows();
    @Select("SELECT * FROM player_show WHERE id=#{id} FOR UPDATE") Show lockShow(int id);
    @Insert("INSERT INTO player_show(user_id,content,images) VALUES(#{userId},#{content},#{images})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insertShow(Show show);
    @Select("SELECT COUNT(*) FROM player_show_like WHERE user_id=#{user} AND player_show_id=#{id}")
    int hasLike(@Param("user") int user,@Param("id") int id);
    @Insert("INSERT INTO player_show_like(user_id,player_show_id) VALUES(#{user},#{id})")
    int like(@Param("user") int user,@Param("id") int id);
    @Delete("DELETE FROM player_show_like WHERE user_id=#{user} AND player_show_id=#{id}")
    int unlike(@Param("user") int user,@Param("id") int id);
    @Update("UPDATE player_show SET likes=likes+#{delta} WHERE id=#{id} AND likes+#{delta}>=0")
    int likes(@Param("id") int id,@Param("delta") int delta);
    @Select("SELECT c.id,c.content,c.created_at AS createdAt,u.account AS username FROM comment c JOIN user u ON u.id=c.user_id WHERE player_show_id=#{id} ORDER BY c.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> comments(@Param("id") int id,@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM comment WHERE player_show_id=#{id}") long countComments(int id);
    @Insert("INSERT INTO comment(player_show_id,user_id,content) VALUES(#{id},#{user},#{text})")
    int comment(@Param("id") int id,@Param("user") int user,@Param("text") String text);
}
