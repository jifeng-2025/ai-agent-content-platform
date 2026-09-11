package com.yupi.template.modelconfig;
import java.nio.file.*;import java.nio.channels.*;import java.nio.file.attribute.*;import java.security.*;import javax.crypto.*;import javax.crypto.spec.*;import java.util.*;
@org.springframework.stereotype.Component
public class ModelVault {
 private final Path root;private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 public ModelVault(@org.springframework.beans.factory.annotation.Value("${models.master-key-dir:./data/model-secrets}") String dir,org.springframework.jdbc.core.JdbcTemplate jdbc){root=Path.of(dir).toAbsolutePath().normalize();this.jdbc=jdbc;}
 private byte[] master()throws Exception{
  Files.createDirectories(root);for(Path p=root;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new Exception();restrict(root,true);
  Path lock=root.resolve("master.lock"),file=root.resolve("master.key");if(Files.isSymbolicLink(lock)||Files.isSymbolicLink(file))throw new Exception();
  try(var ch=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var ignored=ch.lock()){
   restrict(lock,false);if(!Files.exists(file)){if(jdbc.queryForObject("SELECT COUNT(*) FROM model_config WHERE secret IS NOT NULL",Integer.class)>0)throw ModelSpec.bad("MASTER_KEY_MISSING：请恢复原主密钥备份，禁止重新生成");byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);Path temp=Files.createTempFile(root,"key-",".tmp");try{restrict(temp,false);Files.write(temp,bytes);try(var out=FileChannel.open(temp,StandardOpenOption.WRITE)){out.force(true);}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}
   restrict(file,false);byte[] key=Files.readAllBytes(file);if(key.length!=32)throw new Exception();return key;
  }
 }
 private static void restrict(Path path,boolean dir)throws Exception{var posix=Files.getFileAttributeView(path,PosixFileAttributeView.class);if(posix!=null)Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(dir?"rwx------":"rw-------"));else{var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);if(acl==null)throw new Exception();var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();acl.setAcl(List.of(entry));}}
 public synchronized String encrypt(String id,String key){try{byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(master(),"AES"),new GCMParameterSpec(128,iv));c.updateAAD(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(iv)+"."+Base64.getEncoder().encodeToString(c.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(com.yupi.template.exception.BusinessException e){throw e;}catch(Exception e){throw ModelSpec.bad("MASTER_KEY_UNAVAILABLE：检查私有密钥卷及权限");}}
 public synchronized String decrypt(String id,String secret){if(secret==null)throw ModelSpec.bad("MODEL_KEY_MISSING：请管理员配置Key");try{String[] parts=secret.split("[.]");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(master(),"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));c.updateAAD(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));return new String(c.doFinal(Base64.getDecoder().decode(parts[1])),java.nio.charset.StandardCharsets.UTF_8);}catch(com.yupi.template.exception.BusinessException e){throw e;}catch(Exception e){throw ModelSpec.bad("MASTER_KEY_UNAVAILABLE：请恢复正确的主密钥");}}
}
