package com.tianji.learning.service;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author yzp
 * @since 2024-02-06
 */
public interface ILearningLessonService extends IService<LearningLesson> {


    void addUserLesson(Long userId, List<Long> courseIds);
}
