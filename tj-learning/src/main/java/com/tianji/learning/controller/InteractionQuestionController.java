package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author yzp
 * @since 2024-02-08
 */
@Api(tags ="互动问题相关接口")
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    public void saveQuestion(@Validated @RequestBody QuestionFormDTO dto) {
        questionService.saveQuestion(dto);
    }

    @ApiOperation("修改互动问题")
    @PutMapping("{id}")
    public void updateQuestion(@PathVariable Long id, @RequestBody QuestionFormDTO dto) {
        questionService.updateQuestion(id, dto);
    }

    @ApiOperation("分页查询互动问题-用户端")
    @GetMapping("page")
    public PageDTO<QuestionVO> updateQuestion(QuestionPageQuery query) {
        return questionService.pageQuestion(query);

    }

    @ApiOperation("查询问题详情-用户端")
    @GetMapping("{id}")
    public QuestionVO queryQuetionById(@PathVariable Long id) {
        return questionService.queryQuetionById(id);
    }

}
