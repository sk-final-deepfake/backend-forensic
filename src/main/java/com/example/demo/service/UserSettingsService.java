package com.example.demo.service;

import com.example.demo.domain.UserSetting;
import com.example.demo.domain.enums.DateDisplayFormat;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.dto.user.UpdateUserSettingsRequest;
import com.example.demo.dto.user.UserSettingsResponse;
import com.example.demo.repository.UserSettingRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingRepository userSettingRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(Long userId) {
        return toResponse(resolveSettings(userId));
    }

    @Transactional
    public UserSettingsResponse updateSettings(Long userId, UpdateUserSettingsRequest request) {
        UserSetting setting = userSettingRepository.findByUserId(userId)
                .orElseGet(() -> createDefault(userId));

        if (request.getDateDisplayFormat() != null) {
            setting.setDateDisplayFormat(request.getDateDisplayFormat());
        }
        if (request.getAnalysisCompleteNotificationEnabled() != null) {
            setting.setAnalysisCompleteNotificationEnabled(request.getAnalysisCompleteNotificationEnabled());
        }
        if (request.getListViewMode() != null) {
            setting.setListViewMode(request.getListViewMode());
        }
        setting.setUpdatedAt(LocalDateTime.now());

        return toResponse(userSettingRepository.save(setting));
    }

    @Transactional(readOnly = true)
    public boolean isAnalysisNotificationEnabled(Long userId) {
        return resolveSettings(userId).isAnalysisCompleteNotificationEnabled();
    }

    private UserSetting resolveSettings(Long userId) {
        return userSettingRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultTransient(userId));
    }

    private UserSetting createDefault(Long userId) {
        UserSetting setting = createDefaultTransient(userId);
        return userSettingRepository.save(setting);
    }

    private UserSetting createDefaultTransient(Long userId) {
        UserSetting setting = new UserSetting();
        setting.setUserId(userId);
        setting.setDateDisplayFormat(DateDisplayFormat.ISO);
        setting.setAnalysisCompleteNotificationEnabled(true);
        setting.setListViewMode(ListViewMode.TABLE);
        setting.setUpdatedAt(LocalDateTime.now());
        return setting;
    }

    private UserSettingsResponse toResponse(UserSetting setting) {
        return UserSettingsResponse.builder()
                .dateDisplayFormat(setting.getDateDisplayFormat())
                .analysisCompleteNotificationEnabled(setting.isAnalysisCompleteNotificationEnabled())
                .listViewMode(setting.getListViewMode())
                .updatedAt(ApiDateTimeFormatter.formatUtc(setting.getUpdatedAt()))
                .build();
    }
}
