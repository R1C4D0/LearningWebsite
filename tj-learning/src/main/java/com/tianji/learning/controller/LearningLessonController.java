package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author yzp
 * @since 2024-02-06
 */
@Api(tags = "我的课程相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    final ILearningLessonService lessonService;

    @ApiOperation("分页查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return lessonService.queryMyLessons(query);

    }

    @GetMapping("/now")
    @ApiOperation("查询当前用户正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson(){
        return lessonService.queryMyCurrentLesson();
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("查询当前用户课程是否有效")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}")
    @ApiOperation("查询当前用户的课表中是否有该课程")
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId") Long courseId){
        return lessonService.queryLessonByCourseId(courseId);
    }

    @PostMapping("plans")
    @ApiOperation("创建学习计划")
    public void createLearningPlan(@RequestBody @Validated LearningPlanDTO dto){
        lessonService.createLearningPlan(dto);
    }

    @GetMapping("plans")
    @ApiOperation("分页查询我的课程计划")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return lessonService.queryMyPlans(query);
    }

}
