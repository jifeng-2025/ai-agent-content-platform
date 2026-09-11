package com.yupi.template.runtime;
import com.yupi.template.common.*;
import com.yupi.template.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
@RestController @RequestMapping("/article/{taskId}/runtime") @RequiredArgsConstructor
public class RuntimeController {
 private final RuntimeControl control;private final UserService users;
 @GetMapping public BaseResponse<Map<String,Object>> get(@PathVariable String taskId,HttpServletRequest r){return ResultUtils.success(control.view(taskId,users.getLoginUser(r)));}
 @PostMapping public BaseResponse<Map<String,Object>> submit(@PathVariable String taskId,@RequestBody RuntimeControl.Command c,HttpServletRequest r){return ResultUtils.success(control.submit(taskId,c,users.getLoginUser(r)));}
}
