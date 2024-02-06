package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-06
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    final CourseClient courseClient;

    @Override
    public void addUserLesson(Long userId, List<Long> courseIds) {
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);

        List<LearningLesson> list = new ArrayList<>();
        for (CourseSimpleInfoDTO cinfo : cinfos) {
            LearningLesson lesson = new LearningLesson();
            lesson.setUserId(userId);
            lesson.setCourseId(cinfo.getId());
            Integer validDuration = cinfo.getValidDuration();
            if (validDuration != null) {
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusDays(validDuration));
            }

            list.add(lesson);
        }
        this.saveBatch(list);
    }
}
