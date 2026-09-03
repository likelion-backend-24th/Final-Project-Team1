ALTER TABLE expos
    ADD CONSTRAINT chk_expos_category
        CHECK (category IN ('IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타'));
