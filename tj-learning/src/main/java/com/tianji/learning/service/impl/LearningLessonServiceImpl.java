package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;

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

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
//        获取当前用户
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
//      分页查询当前用户的课程
        Page<LearningLesson> page = this.lambdaQuery().eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
//      获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)) {
            throw new BizIllegalException("课程信息不存在");
        }
//        构建课程信息map
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
//        构建VO返回
        List<LearningLessonVO> voList = new ArrayList<>();

        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);

            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId());

            if (infoDTO != null) {
                vo.setCourseName(infoDTO.getName());
                vo.setCourseCoverUrl(infoDTO.getCoverUrl());
                vo.setSections(infoDTO.getSectionNum());
            }

            voList.add(vo);
        }
        return PageDTO.of(page, voList);

    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
//          1.获取当前登录的用户
        Long user = UserContext.getUser();
        if (user == null) {
            throw new BadRequestException("用户未登录");
        }

//         2.查询正在学习的课程 select * from xx where user_id = #{userId} AND status = 1 order by latest_learn_time limit 1
        LearningLesson lesson = this.lambdaQuery().eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1").one();
        if (lesson == null) {
            return null;
        }

        // 3.远程调用课程服务，给VO中的课程名 封面 章节数量赋值
        CourseFullInfoDTO courseinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);

        if (courseinfo == null) {
            throw new BizIllegalException("课程信息不存在");
        }

        // 4.查询当前用户课表中 已报名的总课程数
        Integer count = this.lambdaQuery().eq(LearningLesson::getUserId, user).count();


        // 5.通过feign远程调用课程服务，获取小节名称和编号
        Long latestSectionId = lesson.getLatestSectionId();
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)) {
            throw new BizIllegalException("小节信息不存在");
        }

        // 6.封装到VO返回

        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(courseinfo.getName());
        vo.setCourseCoverUrl(courseinfo.getCoverUrl());
        vo.setSections(courseinfo.getSectionNum());
        vo.setCourseAmount(count);
//        设置最新学习的小节信息
        CataSimpleInfoDTO simpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionIndex(simpleInfoDTO.getCIndex());
        vo.setLatestSectionName(simpleInfoDTO.getName());

        return vo;

    }

    @Override
    public Long isLessonValid(Long courseId) {
//        1.获取当前登录的用户
        Long user = UserContext.getUser();
        if (user == null) {
            throw new BadRequestException("用户未登录");
        }
//        2.查询learning_lesson表中是否存在该课程
        LearningLesson lesson = this.lambdaQuery().eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
//        3.判断课程是否有效
        LocalDateTime expireTime = lesson.getExpireTime();
        if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
            return null;
        }
        return lesson.getId();

    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
//        1.获取当前登录的用户
        Long user = UserContext.getUser();
        if (user == null) {
            throw new BadRequestException("用户未登录");
        }
//        2.查询learning_lesson表中是否存在该课程
        LearningLesson lesson = this.lambdaQuery().eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson == null) {
            return null;
        }
//        3.po转vo
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    @Override
    public void createLearningPlan(LearningPlanDTO dto) {
        Long user = UserContext.getUser();
        if (user == null) {
            throw new BadRequestException("用户未登录");
        }

        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, user)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();

//        修改课表
        this.lambdaUpdate()
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .set(LearningLesson::getWeekFreq, dto.getFreq())
                .eq(LearningLesson::getId, lesson.getId())
                .update();

    }

    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
//        1.获取当前登录的用户
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
//        Todo
//      2.查询积分

//        3.查询本周学习计划总数据
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id", userId);
        wrapper.in("status", LessonStatus.NOT_BEGIN, LessonStatus.LEARNING);
        wrapper.eq("plan_status", PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper);
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null) {
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }
//        4.查询本周已完成学习计划数
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);

        Integer weekFinishedPlanCount = recordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));
//        5.查询本周学习计划列表
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));

        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;

        }
//        6、远程调用课程服务，获取课程信息
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> courseSimpleInfoDTOList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(courseSimpleInfoDTOList)) {
            throw new BizIllegalException("课程信息不存在");
        }
//        将courseSimpleInfoDTOList转换为map <courseId, CourseSimpleInfoDTO>
        Map<Long, CourseSimpleInfoDTO> courseSimpleInfoDTOMap = courseSimpleInfoDTOList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

//        7.查询学习记录表learning_record 本周 当前用户 每一门课 已学习的小节数
        QueryWrapper<LearningRecord> learningRecordQueryWrapper = new QueryWrapper<>();
//        用userId暂时存放一下
        learningRecordQueryWrapper.select("lesson_id as lessonId", "count(*) as userId");
        learningRecordQueryWrapper.eq("user_id", userId);
        learningRecordQueryWrapper.eq("finished", true);
        learningRecordQueryWrapper.between("finish_time", weekBeginTime, weekEndTime);
        learningRecordQueryWrapper.groupBy("lesson_id");
        List<LearningRecord> learningRecords = recordMapper.selectList(learningRecordQueryWrapper);
//        将learningRecords转换为map <lessonId, userId>, userId存放的是当前用户对该课程已学习的小节数
        Map<Long, Long> courseWeekFinishedNumMap = learningRecords.stream().collect(Collectors.toMap(LearningRecord::getLessonId, c -> c.getUserId()));

//        8.封装到VO返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekFinished(weekFinishedPlanCount);
        vo.setWeekTotalPlan(plansTotal);

        ArrayList<LearningPlanVO> voList = new ArrayList<>();
        records.forEach(lesson -> {
            LearningPlanVO learningPlanVO = BeanUtils.copyBean(lesson, LearningPlanVO.class);
            CourseSimpleInfoDTO courseSimpleInfoDTO = courseSimpleInfoDTOMap.get(lesson.getCourseId());
            if (courseSimpleInfoDTO != null) {
                learningPlanVO.setCourseName(courseSimpleInfoDTO.getName());//课程名称
                learningPlanVO.setSections(courseSimpleInfoDTO.getSectionNum());//课程总节数

            }
            learningPlanVO.setWeekLearnedSections(courseWeekFinishedNumMap.getOrDefault(lesson.getId(), 0L).intValue());//本周已学习的小节数

            voList.add(learningPlanVO);
        });

        return vo.pageInfo(page.getTotal(), page.getPages(), voList);
    }


}
