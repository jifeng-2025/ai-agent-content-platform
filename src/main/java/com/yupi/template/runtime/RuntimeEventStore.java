package com.yupi.template.runtime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class RuntimeEventStore {
 private final JdbcTemplate jdbc;private final RuntimeStore store;
 public record Page(long latest,long floor,String status,List<Map<String,Object>> events) {}
 @Transactional(readOnly=true) public Page read(String id,long cursor){
  var r=store.runtime(id);long latest=RuntimeStore.number(r,"lastEventId");
  Long floor=jdbc.queryForObject("SELECT MIN(seq) FROM article_event WHERE taskId=?",Long.class,id);
  String status=jdbc.queryForObject("SELECT status FROM article WHERE taskId=?",String.class,id);
  var events=jdbc.queryForList("SELECT seq,payloadJson FROM article_event WHERE taskId=? AND seq>? ORDER BY seq LIMIT 100",id,cursor);
  return new Page(latest,floor==null?latest+1:floor,status,events);
 }
}
