package com.personal_finance.backend.repository;

import com.personal_finance.backend.model.MerchantCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, String> {
}
