package com.blindbox.api;

import com.blindbox.common.*;
import com.blindbox.core.*;
import com.blindbox.core.Models.*;
import com.blindbox.security.Principal;
import com.blindbox.messaging.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final PlatformMapper db;private final UserService users;private final CatalogService catalog;
    private final DrawService draws;private final CommunityService community;private final ImageService images;
    private final NotificationService notifications;
    public ApiController(PlatformMapper db,UserService users,CatalogService catalog,DrawService draws,
        CommunityService community,ImageService images,NotificationService notifications) {
        this.db=db;this.users=users;this.catalog=catalog;this.draws=draws;this.community=community;this.images=images;
        this.notifications=notifications;
    }
    @GetMapping("/user/notifications") public Result<?> notifications(@AuthenticationPrincipal Principal user) {
        return Result.success(notifications.list(user.id()));
    }
    @PostMapping("/auth/register") public Result<?> register(@Valid @RequestBody Credentials request) {
        users.register(request);return Result.success("注册成功",Map.of());
    }
    @PostMapping("/auth/login") public Result<?> login(@Valid @RequestBody LoginCredentials request) {
        return Result.success(users.login(request));
    }
    @GetMapping("/user/info") public Result<?> info(@AuthenticationPrincipal Principal user) {return Result.success(users.info(user.id()));}
    @PostMapping("/user/recharge") public Result<?> recharge(@AuthenticationPrincipal Principal user,
        @Valid @RequestBody Recharge request,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return Result.success(users.recharge(user.id(),request.amount(),key));
    }
    @GetMapping("/blindboxes") public Result<?> list(@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="12") int pageSize,@RequestParam(defaultValue="") String search,
        @RequestParam(defaultValue="") String series) {
        return Result.success(catalog.list(page,pageSize,series,search,false));
    }
    @GetMapping("/blindboxes/{id}") public Result<?> detail(@PathVariable int id,@AuthenticationPrincipal Principal user) {
        return Result.success(catalog.detail(id,user!=null && "ADMIN".equals(user.role())));
    }
    @PostMapping("/blindboxes/{id}/draw") public Result<?> draw(@AuthenticationPrincipal Principal user,@PathVariable int id,
        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return Result.success(draws.result(draws.draw(user.id(),id,key)));
    }
    @GetMapping("/orders/request/{key}") public Result<?> result(@AuthenticationPrincipal Principal user,@PathVariable String key) {
        DrawService.requestKey(key);Order order=db.findOrderByRequest(user.id(),key);
        if(order==null) throw new BusinessException(404,"尚未查询到结果，请保留请求 ID 并稍后确认");
        return Result.success(draws.result(order));
    }
    @GetMapping("/orders") public Result<?> orders(@AuthenticationPrincipal Principal user,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        CatalogService.page(page);CatalogService.size(pageSize);
        return Result.success(CatalogService.paged(db.orders(user.id(),pageSize,(page-1)*pageSize),db.countOrders(user.id()),page,pageSize));
    }
    @GetMapping("/user/collection") public Result<?> collection(@AuthenticationPrincipal Principal user,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int pageSize) {
        CatalogService.page(page);CatalogService.size(pageSize);Map<String,Object> stats=db.collectionStats(user.id());
        Number unique=(Number)stats.get("uniqueItems");
        Map<String,Object> result=new HashMap<>(CatalogService.paged(db.collection(user.id(),pageSize,(page-1)*pageSize),unique.longValue(),page,pageSize));
        result.put("stats",stats);return Result.success(result);
    }
    @GetMapping("/playershows") public Result<?> shows(@AuthenticationPrincipal Principal user,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="6") int pageSize) {
        return Result.success(community.list(user==null?0:user.id(),page,pageSize));
    }
    @PostMapping("/playershows") public Result<?> publish(@AuthenticationPrincipal Principal user,@Valid @RequestBody Publish request) {
        community.publish(user.id(),request);return Result.success(Map.of());
    }
    @PostMapping("/playershows/{id}/like") public Result<?> like(@AuthenticationPrincipal Principal user,@PathVariable int id) {
        return Result.success(community.like(user.id(),id,true));
    }
    @DeleteMapping("/playershows/{id}/like") public Result<?> unlike(@AuthenticationPrincipal Principal user,@PathVariable int id) {
        return Result.success(community.like(user.id(),id,false));
    }
    @GetMapping("/playershows/{id}/comments") public Result<?> comments(@PathVariable int id,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize) {
        return Result.success(community.comments(id,page,pageSize));
    }
    @PostMapping("/playershows/{id}/comment") public Result<?> comment(@AuthenticationPrincipal Principal user,
        @PathVariable int id,@Valid @RequestBody Comment request) {
        community.comment(user.id(),id,request.content());return Result.success(Map.of());
    }
    @PostMapping("/images") public Result<?> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(Map.of("url",images.upload(file)));
    }
    @GetMapping("/admin/blindboxes") public Result<?> adminList(@RequestParam(defaultValue="1") int page,
        @RequestParam(defaultValue="10") int pageSize,@RequestParam(defaultValue="") String search) {
        return Result.success(catalog.list(page,pageSize,"",search,true));
    }
    @PostMapping("/admin/blindboxes") public Result<?> create(@AuthenticationPrincipal Principal user,@Valid @RequestBody CreateBox request,
        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return Result.success(catalog.create(user.id(),request,key));
    }
    @PutMapping("/admin/blindboxes/{id}") public Result<?> update(@PathVariable int id,@Valid @RequestBody UpdateBox request) {
        catalog.update(id,request);return Result.success(Map.of());
    }
    @PatchMapping("/admin/blindboxes/{id}/status") public Result<?> status(@PathVariable int id,@Valid @RequestBody Status request) {
        catalog.status(id,request.status());return Result.success(Map.of());
    }
    @DeleteMapping("/admin/blindboxes/{id}") public Result<?> archive(@PathVariable int id) {
        catalog.status(id,"ARCHIVED");return Result.success(Map.of());
    }
    @PostMapping("/admin/blindboxes/{id}/restock") public Result<?> restock(@AuthenticationPrincipal Principal user,
        @PathVariable int id,@Valid @RequestBody Restock request,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        catalog.restock(user.id(),id,request,key);return Result.success(Map.of());
    }
}
