package com.yupi.template.controller;
import com.yupi.template.common.*;
import com.yupi.template.model.dto.article.*;
import com.yupi.template.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/article/{taskId}/interventions")
@RequiredArgsConstructor
public class ArticleInterventionController {
    private final UserService users;
    private final ArticleInterventionService interventions;
    private final ArticleInterventionWorker worker;
    @GetMapping
    public BaseResponse<InterventionView> view(@PathVariable String taskId,HttpServletRequest request) {
        return ResultUtils.success(interventions.view(taskId,users.getLoginUser(request)));
    }
    @PostMapping
    public BaseResponse<ArticleInterventionService.Receipt> submit(@PathVariable String taskId,@RequestBody InterventionRequest body,HttpServletRequest request) {
        var receipt=interventions.submit(taskId,body,users.getLoginUser(request));
        if(!receipt.replayed()) {
            try { worker.execute(taskId,receipt.requestId()); }
            catch(RuntimeException e) { interventions.fail(taskId,receipt.requestId());throw e; }
        }
        return ResultUtils.success(receipt);
    }
}
