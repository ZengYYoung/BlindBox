package com.blindbox.core;

import com.blindbox.common.BusinessException;
import com.blindbox.core.Models.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CommunityService {
    private final PlatformMapper db;private final ObjectMapper json;
    public CommunityService(PlatformMapper db,ObjectMapper json) {this.db=db;this.json=json;}
    public Map<String,Object> list(int user,int page,int size) {
        CatalogService.page(page);CatalogService.size(size);
        List<Map<String,Object>> items=new ArrayList<>();
        for(Show s:db.shows(user,size,(page-1)*size)) {
            List<String> images;
            try {images=json.readValue(s.getImages(),new TypeReference<List<String>>(){});}
            catch(Exception e) {images=List.of();}
            items.add(Map.of("id",s.getId(),"username",s.getUsername(),"content",s.getContent()==null?"":s.getContent(),
                "images",images,"likes",s.getLikes(),"liked",Boolean.TRUE.equals(s.getLiked()),"createdAt",s.getCreatedAt()));
        }
        return CatalogService.paged(items,db.countShows(),page,size);
    }
    @Transactional
    public void publish(int user,Publish request) {
        List<String> images=request.images()==null?List.of():request.images();
        for(String path:images) if(!path.matches("/uploads/[a-f0-9-]+[.]png"))
            throw new BusinessException(400,"晒单图片必须来自图片上传接口");
        Show show=new Show();show.setUserId(user);show.setContent(request.content().trim());
        try {show.setImages(json.writeValueAsString(images));}
        catch(Exception e) {throw new IllegalStateException(e);}
        db.insertShow(show);
    }
    @Transactional(timeout=5)
    public Map<String,Object> like(int user,int id,boolean add) {
        Show show=db.lockShow(id);if(show==null) throw new BusinessException(404,"晒单不存在");
        boolean exists=db.hasLike(user,id)>0;int delta=0;
        if(add && !exists) {db.like(user,id);delta=1;}
        if(!add && exists) {db.unlike(user,id);delta=-1;}
        if(delta!=0) DrawService.requireOne(db.likes(id,delta),"点赞计数异常");
        return Map.of("likes",show.getLikes()+delta,"liked",add);
    }
    @Transactional
    public void comment(int user,int id,String text) {
        if(db.lockShow(id)==null) throw new BusinessException(404,"晒单不存在");
        db.comment(id,user,text.trim());
    }
    public Map<String,Object> comments(int id,int page,int size) {
        CatalogService.page(page);CatalogService.size(size);
        return CatalogService.paged(db.comments(id,size,(page-1)*size),db.countComments(id),page,size);
    }
}
