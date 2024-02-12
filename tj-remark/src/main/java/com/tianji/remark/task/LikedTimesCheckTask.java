package com.tianji.remark.task;


import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private static final List<String> BIZ_TYPE = List.of("QA", "NOTE");//需要检查的业务类型
    private static final int MAX_BIZ_SIZE = 50;//每次最多取的BIZ数量
    private final ILikedRecordService likedRecordService;

    @Scheduled(fixedDelay = 20000)//每间隔20秒执行一次
    public void checkLikedTimes() {
        for (String bizType : BIZ_TYPE) {
            likedRecordService.readLikedTimesAndSendMessage(bizType, MAX_BIZ_SIZE);
        }
    }

}
