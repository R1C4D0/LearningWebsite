package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-07
 */

@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;
    private final LearningRecordDelayTaskHandler delayTaskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
//        1.获取当前用户ID
        Long user = UserContext.getUser();
        if (user == null) {
            throw new RuntimeException("用户未登录");
        }
//        2.查询课表信息 条件：userId, courseId
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();

//        3.查询学习记录
        List<LearningRecord> records = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();

//        4.封装返回结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtoList = BeanUtils.copyList(records, LearningRecordDTO.class);
        dto.setRecords(dtoList);

        return dto;


    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
//        1.获取当前用户ID
        Long user = UserContext.getUser();
        if (user == null) {
            throw new RuntimeException("用户未登录");
        }
//        2.处理学习记录
        boolean isFirstFinished = false;
        if (dto.getSectionType().equals(SectionType.VIDEO)) {
            isFirstFinished = handleVideoRecord(dto, user);
        } else {
            isFirstFinished = handleExamRecord(dto, user);
        }
//  不是第一次学完，不需要更新课表信息
        if (!isFirstFinished){
            return;
        }
        handleLessonDate(dto);

    }

    private void handleLessonDate(LearningRecordFormDTO dto) {
//        1.查询课表learning_lesson
        LearningLesson lesson = lessonService.getById(dto.getLessonId());
        if (lesson == null) {
            throw new RuntimeException("课程不存在");
        }

        boolean allFinished = false;
//        2.判断是否为第一次学完

//        3.远程调用课程服务，得到课程信息
            CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cinfo == null) {
                throw new RuntimeException("课程不存在");
            }
            Integer sectionNum = cinfo.getSectionNum();
//            4.如果isFinished = true，表示本小节是第一次学完，判断该用户该课程是否全部学完
            allFinished = lesson.getLearnedSections() + 1 >= sectionNum;
//        5.更新课表信息
        boolean updateResult = lessonService.lambdaUpdate()
                .set(lesson.getStatus() == LessonStatus.NOT_BEGIN, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allFinished, LearningLesson::getStatus, LessonStatus.FINISHED)
                .set(LearningLesson::getLatestLearnTime, dto.getCommitTime())
                .set(LearningLesson::getLatestSectionId, dto.getSectionId())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, dto.getLessonId())
                .update();
        if (!updateResult) {
            throw new RuntimeException("更新课表信息失败");
        }
    }

    private boolean handleVideoRecord(LearningRecordFormDTO dto, Long user) {
//        1.查询旧的学习记录
//        LearningRecord learningRecord = this.lambdaQuery()
//                .eq(LearningRecord::getLessonId, dto.getLessonId())
//                .eq(LearningRecord::getSectionId, dto.getSectionId())
//                .one();
//        改进：从缓存中获取学习记录
        LearningRecord learningRecord = queryCacheRecord(dto.getLessonId(), dto.getSectionId());

//        2.判断是否存在
//        3.如果不存在，新增
        if (learningRecord == null) {
            LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
            record.setUserId(user);
            boolean result = this.save(record);
            if (!result) {
                throw new RuntimeException("新增学习记录失败");
            }
            return false;
        }

//        4.如果存在，更新学习记录moment字段，判断是否完成
//        判断是否第一次学完，isFirstFinished = true表示第一次学完
        boolean isFirstFinished = !learningRecord.getFinished() && dto.getMoment() * 2 >= dto.getDuration();
        if (!isFirstFinished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(dto.getLessonId());
            record.setSectionId(dto.getSectionId());
            record.setId(learningRecord.getId());
            record.setMoment(dto.getMoment());
            record.setFinished(learningRecord.getFinished());
            delayTaskHandler.addLearningRecordTask(record);
//           返回，不更新数据库
            return false;
        }
//        不是第一次学完，更新数据库
        boolean updateResult = this.lambdaUpdate()
                .set(LearningRecord::getMoment, dto.getMoment())
                .set(isFirstFinished, LearningRecord::getFinished, true)
                .set(isFirstFinished, LearningRecord::getFinishTime, dto.getCommitTime())
                .eq(LearningRecord::getId, learningRecord.getId())
                .update();
        if (!updateResult) {
            throw new RuntimeException("更新学习记录失败");
        }
//      若更新数据库，需要清理redis缓存
        delayTaskHandler.cleanRecordCache(dto.getLessonId(), dto.getSectionId());
        return true;

    }

    private LearningRecord queryCacheRecord(Long lessonId, Long sectionId) {
//      1.查询缓存
        LearningRecord recordCache = delayTaskHandler.readRecordCache(lessonId, sectionId);
//        2.命中返回
        if (recordCache != null) {
            return recordCache;
        }
//        3.未命中查询数据库
        LearningRecord recordDb = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        if (recordDb == null){
            return null;
        }
//        4.存入缓存
        delayTaskHandler.writeRecordCache(recordDb);
        return recordDb;
    }

    private boolean handleExamRecord(LearningRecordFormDTO dto, Long user) {
//        1.dto转po
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        record.setUserId(user);
        record.setFinished(true);
        record.setFinishTime(dto.getCommitTime());
//          2.保存学习记录
        boolean result = this.save(record);
        if (!result) {
            throw new RuntimeException("保存学习记录失败");
        }
        return true;
    }
}
