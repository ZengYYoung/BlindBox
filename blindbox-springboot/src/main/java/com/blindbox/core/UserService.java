package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.core.Models.*;
import com.blindbox.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class UserService {
    private final PlatformMapper db;private final PasswordEncoder encoder;private final JwtService jwt;private final boolean demo;
    public UserService(PlatformMapper db,PasswordEncoder encoder,JwtService jwt,@Value("${app.demo-recharge:false}") boolean demo) {
        this.db=db;this.encoder=encoder;this.jwt=jwt;this.demo=demo;
    }
    @Transactional
    public void register(Credentials credentials) {
        if(credentials.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new BusinessException(400,"密码的 UTF-8 编码不能超过 72 字节");
        if(db.userByAccount(credentials.account())!=null) throw new BusinessException(409,"账号已存在");
        User user=new User();user.setAccount(credentials.account());user.setPassword(encoder.encode(credentials.password()));
        db.insertUser(user);
    }
    public Map<String,Object> login(LoginCredentials credentials) {
        if(credentials.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new BusinessException(401,"账号或密码错误");
        User user=db.userByAccount(credentials.account());
        if(user==null || !encoder.matches(credentials.password(),user.getPassword())) throw new BusinessException(401,"账号或密码错误");
        return Map.of("token",jwt.generate(user.getId()),"user",view(user));
    }
    public Map<String,Object> info(int id) {
        User user=db.user(id);if(user==null) throw new BusinessException(401,"用户不存在");
        return view(user);
    }
    private Map<String,Object> view(User user) {
        return Map.of("id",user.getId(),"account",user.getAccount(),"balance",user.getBalance(),
            "role",user.getRole(),"createdAt",user.getCreatedAt(),"demoRechargeEnabled",demo);
    }
    @Transactional(timeout=10)
    public Map<String,Object> recharge(int id,long amount,String key) {
        if(!demo) throw new BusinessException(403,"开发充值已禁用；真实入账需接入支付回调");
        DrawService.requestKey(key);
        User user=db.lockUser(id);if(user==null) throw new BusinessException(401,"用户不存在");
        Wallet prior=db.rechargeByRequest(id,key);
        if(prior!=null) {
            if(prior.getAmount()!=amount) throw new BusinessException(409,"该请求 ID 的充值金额不同");
            return Map.of("balance",user.getBalance(),"credited",false);
        }
        DrawService.requireOne(db.credit(id,amount),"余额超过允许上限");
        DrawService.requireOne(db.wallet(id,key,"RECHARGE",amount,user.getBalance()+amount,null),"资金流水失败");
        return Map.of("balance",user.getBalance()+amount,"credited",true);
    }
}
