/*
 * Copyright (C) 2023 杭州白书科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.playedu.api.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import xyz.playedu.api.event.UserCourseRecordDestroyEvent;
import xyz.playedu.api.service.UserCourseHourRecordService;

/**
 * @Author 杭州白书科技有限公司
 *
 * @create 2023/4/4 10:16
 */
@Component
public class UserCourseRecordDestroyListener {

    @Autowired private UserCourseHourRecordService userCourseHourRecordService;

    @EventListener
    public void emptyUserCourseHourRecords(UserCourseRecordDestroyEvent event) {
        userCourseHourRecordService.remove(event.getUserId(), event.getCourseId());
    }
}
