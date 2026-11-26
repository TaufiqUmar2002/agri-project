package com.umar.notification.repository;

import com.umar.notification.domain.MessageTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplateEntity, UUID> {
    Optional<MessageTemplateEntity> findByCodeAndLanguageAndVersion(String code, String language, int version);
    Optional<MessageTemplateEntity> findFirstByCodeAndLanguageAndCurrentVersionTrue(String code, String language);
    Optional<MessageTemplateEntity> findFirstByCodeAndLanguageOrderByVersionDesc(String code, String language);
    List<MessageTemplateEntity> findByCodeAndLanguage(String code, String language);
}
