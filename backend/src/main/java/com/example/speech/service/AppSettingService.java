package com.example.speech.service;







import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;



import com.example.speech.config.DeepSeekProperties;



import com.example.speech.entity.AppSetting;



import com.example.speech.mapper.AppSettingMapper;



import java.nio.charset.StandardCharsets;



import java.security.GeneralSecurityException;



import java.security.SecureRandom;



import java.time.LocalDateTime;



import java.util.Base64;



import javax.crypto.Cipher;



import javax.crypto.spec.GCMParameterSpec;



import javax.crypto.spec.SecretKeySpec;



import org.springframework.beans.factory.annotation.Value;



import org.springframework.stereotype.Service;



import org.springframework.transaction.annotation.Transactional;







@Service



public class AppSettingService {



    private static final String DEEPSEEK_KEY = "deepseek.api-key";



    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";



    private static final int IV_LENGTH = 12;



    private static final int TAG_LENGTH = 128;







    private final AppSettingMapper settingMapper;



    private final DeepSeekProperties properties;



    private final SecretKeySpec encryptionKey;







    public AppSettingService(AppSettingMapper settingMapper,



                             DeepSeekProperties properties,



                             @Value("${app.settings-encryption-key:}") String encryptionKey) {



        this.settingMapper = settingMapper;



        this.properties = properties;



        this.encryptionKey = createEncryptionKey(encryptionKey);



    }







    public boolean isConfigured() {



        return findSetting() != null || (properties.apiKey() != null && !properties.apiKey().isBlank());



    }







    public String getApiKey() {



        AppSetting setting = findSetting();



        if (setting == null) return properties.apiKey();



        try {



            return decrypt(setting.getSettingValue());



        } catch (GeneralSecurityException exception) {



            throw new IllegalStateException("DeepSeek API Key 无法解密，请检查 APP_SETTINGS_ENCRYPTION_KEY", exception);



        }



    }







    @Transactional



    public void updateApiKey(String apiKey, Long updateBy) {



        if (encryptionKey == null) {



            throw new IllegalStateException("未配置 APP_SETTINGS_ENCRYPTION_KEY，无法保存 API Key");



        }



        AppSetting setting = findSetting();



        boolean existing = setting != null;

        if (setting == null) {



            setting = new AppSetting();



            setting.setSettingKey(DEEPSEEK_KEY);



        }



        try {



            setting.setSettingValue(encrypt(apiKey));



        } catch (GeneralSecurityException exception) {



            throw new IllegalStateException("DeepSeek API Key 加密失败", exception);



        }



        setting.setUpdateBy(updateBy);



        setting.setUpdateTime(LocalDateTime.now());



        if (existing) settingMapper.updateById(setting);



        else settingMapper.insert(setting);



    }







    private AppSetting findSetting() {



        return settingMapper.selectOne(new LambdaQueryWrapper<AppSetting>()



                .eq(AppSetting::getSettingKey, DEEPSEEK_KEY));



    }







    private String encrypt(String value) throws GeneralSecurityException {



        byte[] iv = new byte[IV_LENGTH];



        new SecureRandom().nextBytes(iv);



        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);



        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH, iv));



        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));



        return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);



    }







    private String decrypt(String value) throws GeneralSecurityException {



        String[] parts = value.split(":", 2);



        if (parts.length != 2) throw new GeneralSecurityException("密文格式无效");



        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);



        cipher.init(Cipher.DECRYPT_MODE, encryptionKey,



                new GCMParameterSpec(TAG_LENGTH, Base64.getDecoder().decode(parts[0])));



        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);



    }







    private static SecretKeySpec createEncryptionKey(String value) {



        if (value == null || value.isBlank()) return null;



        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);



        if (bytes.length != 32) {



            throw new IllegalStateException("APP_SETTINGS_ENCRYPTION_KEY 必须是 32 字节");



        }



        return new SecretKeySpec(bytes, "AES");



    }



}



