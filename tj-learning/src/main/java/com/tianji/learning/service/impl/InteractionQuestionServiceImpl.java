package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CategoryClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService interactionReplyService;
    private final UserClient userClient;
    private final SearchClient searchClient;
    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        Long userId = UserContext.getUser();
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(dto, InteractionQuestion.class);
        interactionQuestion.setUserId(userId);

        this.save(interactionQuestion);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
//        1.检验参数
        if (StringUtils.isBlank(dto.getTitle()) || StringUtils.isBlank(dto.getDescription()) || dto.getAnonymity() == null) {
            throw new BadRequestException("非法参数");
        }
        InteractionQuestion interactionQuestion = this.getById(id);
        if (interactionQuestion == null) {
            throw new BadRequestException("问题不存在");
        }
//        只能修改自己的互动问题
        if (!interactionQuestion.getUserId().equals(UserContext.getUser())) {
            throw new BadRequestException("无权限修改");
        }
//        2.dto转po
        interactionQuestion.setTitle(dto.getTitle());
        interactionQuestion.setDescription(dto.getDescription());
        interactionQuestion.setAnonymity(dto.getAnonymity());
//        3.更新
        this.updateById(interactionQuestion);
    }

    @Override
    public PageDTO<QuestionVO> pageQuestion(QuestionPageQuery query) {
//        1.校验
        if (query.getCourseId() == null) {
            throw new BadRequestException("courseId不能为空");
        }
//        2.获取登录用户
        Long userId = UserContext.getUser();
//        3.分页查询互动问题interaction_question 条件:courseId..
        Page<InteractionQuestion> interactionQuestionPage = this.lambdaQuery()
                .select(InteractionQuestion.class, tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))//排除description字段(太大且无用)
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = interactionQuestionPage.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(interactionQuestionPage);
        }
//        4.根据interaction_question的latest_answer_id去interaction_reply获取最新回答的相关信息
//        互动问题的最新回答id集合
        Set<Long> latestAnswerIds = records.stream()
                .map(InteractionQuestion::getLatestAnswerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        //互动问题的提问者和回答者id集合
        Set<Long> userIds = records.stream()
                .filter(interactionQuestion -> !interactionQuestion.getAnonymity())
                .map(InteractionQuestion::getUserId)
                .collect(Collectors.toSet());

//            key:互动问题的最新回答id value:回答的详细信息
        Map<Long, InteractionReply> replyMap = new HashMap<>();
        if (CollUtils.isNotEmpty(latestAnswerIds)) {//有回答
//            List<InteractionReply> replyList = interactionReplyService.listByIds(latestAnswerIds);
//            查找不匿名的回答者
            List<InteractionReply> replyList = interactionReplyService.list(Wrappers.<InteractionReply>lambdaQuery()
                    .in(InteractionReply::getId, latestAnswerIds)
                    .eq(InteractionReply::getHidden, false));
            for (InteractionReply interactionReply : replyList) {
                if (!interactionReply.getAnonymity()) {
                    userIds.add(interactionReply.getUserId());
                }
                replyMap.put(interactionReply.getId(), interactionReply);
            }
        }
//        5.远程调用课程服务，获取用户信息 批量
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> longUserDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

//        6.封装vo返回
        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionVO questionVO = BeanUtils.copyBean(record, QuestionVO.class);
            if (!questionVO.getAnonymity()) {//提问者不匿名
                UserDTO userDTO = longUserDTOMap.get(record.getUserId());
                if (userDTO != null) {
                    questionVO.setUserName(userDTO.getName());//提问者的名字
                    questionVO.setUserIcon(userDTO.getIcon());//提问者的头像
                }
            }
            InteractionReply reply = replyMap.get(record.getLatestAnswerId());
            if (reply != null) {
                if (!reply.getAnonymity()) {//回答者不匿名
                    UserDTO dto = longUserDTOMap.get(reply.getUserId());
                    if (dto != null) {
                        questionVO.setLatestReplyUser(dto.getName());//最新回答者的名字
                    }
                }
                questionVO.setLatestReplyContent(reply.getContent());//最新回答的内容
            }
            voList.add(questionVO);
        }
        return PageDTO.of(interactionQuestionPage, voList);
    }

    @Override
    public QuestionVO queryQuetionById(Long id) {
//        1.校验
        if (id == null) {
            throw new BadRequestException("id不能为空");
        }
//        2.查询互动问题表，按主键查询
        InteractionQuestion question = this.getById(id);
        if (question == null) {
            throw new BadRequestException("问题不存在");
        }
//        3.如果该问题被隐藏，返回空
        if (question.getHidden()) {
            return null;
        }
//        4.如果用户匿名，不返回名称和头像,封装vo返回
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if (!vo.getAnonymity()) {
//            远程调用用户服务，获取用户信息(名称和头像)
            UserDTO userDTO = userClient.queryUserById(question.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
            }
        }
        return vo;
    }

    @Override
    public PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query) {
//        0.如果传了课程名称    从es中获取该名称对应的课程id
        String courseName = query.getCourseName();
        List<Long> courseIds = null;
        if (StringUtils.isNotBlank(courseName)) {
            courseIds = searchClient.queryCoursesIdByName(courseName);
            if (CollUtils.isEmpty(courseIds)) {
                return PageDTO.empty(0L, 0L);
            }
        }
//        1.查询互动问题表 条件前端传了条件就添加条件 分页 排序按提问时间排序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(courseIds), InteractionQuestion::getCourseId, courseIds)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null, InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(0L, 0L);
        }
        Set<Long> uids = new HashSet<>();
        Set<Long> cids = new HashSet<>();
        Set<Long> chapterAndSectionIds = new HashSet<>();   //章节和小节id
        for (InteractionQuestion record : records) {
            uids.add(record.getUserId());
            cids.add(record.getCourseId());
            chapterAndSectionIds.add(record.getChapterId());
            chapterAndSectionIds.add(record.getSectionId());
        }
//        2.远程调用用户服务，获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        if (CollUtils.isEmpty(userDTOS)) {
            throw new BizIllegalException("用户信息不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

//        3.远程调用课程服务，获取课程信息
        List<CourseSimpleInfoDTO> simpleInfoList = courseClient.getSimpleInfoList(cids);
        if (CollUtils.isEmpty(simpleInfoList)) {
            throw new BizIllegalException("课程信息不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cinfoMap = simpleInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

//        4.远程调用课程服务，获取章节信息
        List<CataSimpleInfoDTO> catalogueList = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (CollUtils.isEmpty(catalogueList)) {
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> catologueMap = catalogueList.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, CataSimpleInfoDTO::getName));


//        5.获取分类信息
//        6.封装vo返回

        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(record, QuestionAdminVO.class);
            UserDTO userDTO = userDTOMap.get(record.getUserId());
            if (userDTO != null) {
                vo.setUserName(userDTO.getName());
            }
            CourseSimpleInfoDTO courseSimpleInfoDTO = cinfoMap.get(record.getCourseId());
            if (courseSimpleInfoDTO != null) {
                vo.setCourseName(courseSimpleInfoDTO.getName());
                List<Long> categoryIds = courseSimpleInfoDTO.getCategoryIds();
                String categoryNames = categoryCache.getCategoryNames(categoryIds);
                vo.setCategoryName(categoryNames);//三级分类名称
            }
            vo.setChapterName(catologueMap.get(record.getChapterId()));//章节名称
            vo.setSectionName(catologueMap.get(record.getSectionId()));//小节名称

            voList.add(vo);
        }

        return PageDTO.of(page, voList);
    }
}
