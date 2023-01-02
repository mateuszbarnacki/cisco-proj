package com.project.translator.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface LanguageRepository extends JpaRepository<LanguageEntity, Long> {
    List<LanguageEntity> findAll();

    Optional<LanguageEntity> findByLanguage(String language);
}
