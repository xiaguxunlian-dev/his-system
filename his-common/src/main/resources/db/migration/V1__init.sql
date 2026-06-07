-- ============================================================
-- 医院信息系统 (HIS) 数据库初始化脚本 V1
-- 兼容: PostgreSQL 16 / H2 2.x (MODE=PostgreSQL)
-- ============================================================

-- ============================================================
-- 1. 系统用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS system_users (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT '挂号员',
    department_id INTEGER,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INTEGER    NOT NULL DEFAULT 0,
    locked_until  TIMESTAMP,
    last_login    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. 操作审计日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id           SERIAL PRIMARY KEY,
    user_id      INTEGER,
    username     VARCHAR(50),
    action       VARCHAR(100) NOT NULL,
    target_table VARCHAR(100),
    target_id    VARCHAR(100),
    detail       TEXT,
    ip_address   VARCHAR(50),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 3. 系统配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS system_configs (
    id          SERIAL PRIMARY KEY,
    config_key  VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    description  VARCHAR(500),
    module       VARCHAR(50),
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 4. 科室表
-- ============================================================
CREATE TABLE IF NOT EXISTS departments (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(50)  NOT NULL DEFAULT '门诊科室',
    location    VARCHAR(200),
    phone       VARCHAR(20),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 5. 医生表
-- ============================================================
CREATE TABLE IF NOT EXISTS doctors (
    id            SERIAL PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    gender        VARCHAR(5)   NOT NULL DEFAULT '男',
    title         VARCHAR(50),
    department_id INTEGER,
    phone         VARCHAR(20),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    schedule_info TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 6. 患者档案表
-- ============================================================
CREATE TABLE IF NOT EXISTS patients (
    id           SERIAL PRIMARY KEY,
    patient_no   VARCHAR(20)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    gender       VARCHAR(5)   NOT NULL DEFAULT '男',
    birth_date   DATE,
    age          INTEGER,
    id_card      VARCHAR(18),
    phone        VARCHAR(20),
    address      TEXT,
    blood_type   VARCHAR(5),
    allergy_info TEXT,
    medical_insurance_type VARCHAR(50) DEFAULT '自费',
    medical_insurance_no   VARCHAR(50),
    emergency_contact      VARCHAR(100),
    emergency_phone        VARCHAR(20),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 7. 挂号记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS registrations (
    id              SERIAL PRIMARY KEY,
    registration_no VARCHAR(30)  NOT NULL UNIQUE,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    department_id   INTEGER,
    department_name VARCHAR(100),
    doctor_id       INTEGER,
    doctor_name     VARCHAR(100),
    visit_type      VARCHAR(20)  NOT NULL DEFAULT '普通',
    is_emergency    BOOLEAN      NOT NULL DEFAULT FALSE,
    visit_date      DATE         NOT NULL,
    visit_time_slot VARCHAR(20),
    queue_no        INTEGER,
    status          VARCHAR(20)  NOT NULL DEFAULT '待就诊',
    registration_fee DECIMAL(10,2) DEFAULT 0.00,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(50)
);

-- ============================================================
-- 8. 号源管理表
-- ============================================================
CREATE TABLE IF NOT EXISTS appointment_slots (
    id            SERIAL PRIMARY KEY,
    doctor_id     INTEGER      NOT NULL,
    doctor_name   VARCHAR(100),
    department_id INTEGER,
    slot_date     DATE         NOT NULL,
    time_slot     VARCHAR(20)  NOT NULL,
    total_quota   INTEGER      NOT NULL DEFAULT 30,
    used_quota    INTEGER      NOT NULL DEFAULT 0,
    slot_type     VARCHAR(20)  NOT NULL DEFAULT '普通',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ============================================================
-- 9. 门诊就诊记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS outpatient_visits (
    id              SERIAL PRIMARY KEY,
    visit_no        VARCHAR(30)  NOT NULL UNIQUE,
    registration_id INTEGER,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    department_id   INTEGER,
    department_name VARCHAR(100),
    doctor_id       INTEGER,
    doctor_name     VARCHAR(100),
    visit_date      DATE         NOT NULL,
    chief_complaint TEXT,
    diagnosis       TEXT,
    diagnosis_code  VARCHAR(20),
    treatment_plan  TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT '接诊中',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 10. 处方表
-- ============================================================
CREATE TABLE IF NOT EXISTS prescriptions (
    id              SERIAL PRIMARY KEY,
    prescription_no VARCHAR(30)  NOT NULL UNIQUE,
    visit_id        INTEGER,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    doctor_id       INTEGER,
    doctor_name     VARCHAR(100),
    prescription_type VARCHAR(20) NOT NULL DEFAULT '普通',
    is_narcotic     BOOLEAN      NOT NULL DEFAULT FALSE,
    total_amount    DECIMAL(10,2) DEFAULT 0.00,
    status          VARCHAR(20)  NOT NULL DEFAULT '待缴费',
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 11. 处方明细表
-- ============================================================
CREATE TABLE IF NOT EXISTS prescription_items (
    id               SERIAL PRIMARY KEY,
    prescription_id  INTEGER      NOT NULL,
    drug_id          INTEGER,
    drug_name        VARCHAR(200) NOT NULL,
    drug_spec        VARCHAR(100),
    dosage           VARCHAR(50),
    usage_method     VARCHAR(100),
    frequency        VARCHAR(50),
    days             INTEGER      DEFAULT 1,
    quantity         DECIMAL(10,2) NOT NULL DEFAULT 1,
    unit             VARCHAR(20),
    unit_price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_price      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    notes            TEXT
);

-- ============================================================
-- 12. 床位表
-- ============================================================
CREATE TABLE IF NOT EXISTS beds (
    id            SERIAL PRIMARY KEY,
    bed_no        VARCHAR(20)  NOT NULL UNIQUE,
    ward_name     VARCHAR(100) NOT NULL,
    department_id INTEGER,
    bed_type      VARCHAR(50)  NOT NULL DEFAULT '普通床',
    status        VARCHAR(20)  NOT NULL DEFAULT '空闲',
    current_patient_id INTEGER,
    notes         TEXT
);

-- ============================================================
-- 13. 住院记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS inpatient_records (
    id              SERIAL PRIMARY KEY,
    admission_no    VARCHAR(30)  NOT NULL UNIQUE,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    department_id   INTEGER,
    department_name VARCHAR(100),
    doctor_id       INTEGER,
    doctor_name     VARCHAR(100),
    bed_id          INTEGER,
    bed_no          VARCHAR(20),
    ward_name       VARCHAR(100),
    admission_date  DATE         NOT NULL,
    discharge_date  DATE,
    admission_reason TEXT,
    admission_diagnosis TEXT,
    discharge_diagnosis TEXT,
    discharge_summary TEXT,
    days_of_stay    INTEGER,
    total_cost      DECIMAL(10,2) DEFAULT 0.00,
    paid_amount     DECIMAL(10,2) DEFAULT 0.00,
    deposit_amount  DECIMAL(10,2) DEFAULT 0.00,
    status          VARCHAR(20)  NOT NULL DEFAULT '在院',
    is_critical     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 14. 住院费用明细表
-- ============================================================
CREATE TABLE IF NOT EXISTS inpatient_charges (
    id              SERIAL PRIMARY KEY,
    admission_id    INTEGER      NOT NULL,
    patient_id      INTEGER,
    charge_type     VARCHAR(50)  NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    quantity        DECIMAL(10,2) NOT NULL DEFAULT 1,
    unit_price      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_price     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    charge_date     DATE,
    doctor_name     VARCHAR(100),
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 15. 手术记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_records (
    id               SERIAL PRIMARY KEY,
    admission_id     INTEGER      NOT NULL,
    patient_id       INTEGER,
    patient_name     VARCHAR(100),
    operation_name   VARCHAR(200) NOT NULL,
    operation_code   VARCHAR(50),
    surgeon_id       INTEGER,
    surgeon_name     VARCHAR(100),
    assistant_names  VARCHAR(200),
    anesthesia_type  VARCHAR(50),
    anesthetist_name VARCHAR(100),
    operation_date   DATE         NOT NULL,
    start_time       TIME,
    end_time         TIME,
    duration_minutes INTEGER,
    status           VARCHAR(20)  NOT NULL DEFAULT '已完成',
    operation_notes  TEXT,
    complications    TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 16. 护理记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS nursing_records (
    id              SERIAL PRIMARY KEY,
    admission_id    INTEGER      NOT NULL,
    patient_id      INTEGER,
    nurse_id        INTEGER,
    nurse_name      VARCHAR(100),
    nursing_type    VARCHAR(100) NOT NULL,
    content         TEXT         NOT NULL,
    vitals_temp     DECIMAL(5,2),
    vitals_pulse    INTEGER,
    vitals_bp_high  INTEGER,
    vitals_bp_low   INTEGER,
    vitals_spo2     INTEGER,
    record_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 17. 药品字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS drugs (
    id            SERIAL PRIMARY KEY,
    drug_code     VARCHAR(30)  NOT NULL UNIQUE,
    generic_name  VARCHAR(200) NOT NULL,
    trade_name    VARCHAR(200),
    drug_type     VARCHAR(50)  NOT NULL DEFAULT '西药',
    spec          VARCHAR(100),
    unit          VARCHAR(20),
    dosage_form   VARCHAR(50),
    manufacturer  VARCHAR(200),
    approval_no   VARCHAR(50),
    is_narcotic   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_psychotropic BOOLEAN    NOT NULL DEFAULT FALSE,
    is_otc        BOOLEAN      NOT NULL DEFAULT FALSE,
    storage_cond  VARCHAR(100),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 18. 药品库存表
-- ============================================================
CREATE TABLE IF NOT EXISTS drug_inventory (
    id             SERIAL PRIMARY KEY,
    drug_id        INTEGER      NOT NULL,
    drug_name      VARCHAR(200) NOT NULL,
    drug_spec      VARCHAR(100),
    batch_no       VARCHAR(50),
    expiry_date    DATE,
    stock_qty      DECIMAL(10,2) NOT NULL DEFAULT 0,
    min_stock_qty  DECIMAL(10,2) NOT NULL DEFAULT 10,
    unit_price     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    retail_price   DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    storage_loc    VARCHAR(100),
    last_updated   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 19. 药品采购单表
-- ============================================================
CREATE TABLE IF NOT EXISTS drug_purchase_orders (
    id            SERIAL PRIMARY KEY,
    order_no      VARCHAR(30)  NOT NULL UNIQUE,
    drug_id       INTEGER      NOT NULL,
    drug_name     VARCHAR(200) NOT NULL,
    supplier      VARCHAR(200),
    batch_no      VARCHAR(50),
    expiry_date   DATE,
    purchase_qty  DECIMAL(10,2) NOT NULL,
    unit_price    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    status        VARCHAR(20)  NOT NULL DEFAULT '已入库',
    operator_name VARCHAR(100),
    purchase_date DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 20. 检查项目字典表
-- ============================================================
CREATE TABLE IF NOT EXISTS exam_items (
    id            SERIAL PRIMARY KEY,
    item_code     VARCHAR(30)  NOT NULL UNIQUE,
    item_name     VARCHAR(200) NOT NULL,
    category      VARCHAR(50)  NOT NULL DEFAULT '检验',
    body_part     VARCHAR(100),
    price         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    unit          VARCHAR(20),
    preparation   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 21. 检查申请单
-- ============================================================
CREATE TABLE IF NOT EXISTS examination_requests (
    id              SERIAL PRIMARY KEY,
    request_no      VARCHAR(30)  NOT NULL UNIQUE,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    visit_id        INTEGER,
    admission_id    INTEGER,
    doctor_id       INTEGER,
    doctor_name     VARCHAR(100),
    department_name VARCHAR(100),
    item_id         INTEGER,
    item_name       VARCHAR(200) NOT NULL,
    category        VARCHAR(50)  NOT NULL DEFAULT '检验',
    exam_body_part  VARCHAR(100),
    is_urgent       BOOLEAN      NOT NULL DEFAULT FALSE,
    clinical_info   TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT '待检查',
    request_date    DATE         NOT NULL DEFAULT CURRENT_DATE,
    exam_date       DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 22. 检查报告表
-- ============================================================
CREATE TABLE IF NOT EXISTS examination_reports (
    id              SERIAL PRIMARY KEY,
    request_id      INTEGER      NOT NULL,
    report_no       VARCHAR(30)  NOT NULL UNIQUE,
    patient_id      INTEGER,
    patient_name    VARCHAR(100),
    item_name       VARCHAR(200),
    findings        TEXT,
    conclusion      TEXT         NOT NULL,
    is_abnormal     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_critical     BOOLEAN      NOT NULL DEFAULT FALSE,
    critical_handled BOOLEAN     NOT NULL DEFAULT FALSE,
    tech_name       VARCHAR(100),
    doctor_name     VARCHAR(100),
    report_date     DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 23. 电子病历表
-- ============================================================
CREATE TABLE IF NOT EXISTS medical_records (
    id              SERIAL PRIMARY KEY,
    record_no       VARCHAR(30)  NOT NULL UNIQUE,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    visit_id        INTEGER,
    admission_id    INTEGER,
    record_type     VARCHAR(50)  NOT NULL DEFAULT '门诊病历',
    department_name VARCHAR(100),
    doctor_id       INTEGER,
    doctor_name     VARCHAR(100),
    chief_complaint TEXT,
    present_illness TEXT,
    past_history    TEXT,
    allergy_history TEXT,
    physical_exam   TEXT,
    auxiliary_exam  TEXT,
    diagnosis       TEXT,
    treatment_plan  TEXT,
    record_content  TEXT,
    is_locked       BOOLEAN      NOT NULL DEFAULT FALSE,
    locked_at       TIMESTAMP,
    visit_date      DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 24. 收费记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS billing_records (
    id              SERIAL PRIMARY KEY,
    bill_no         VARCHAR(30)  NOT NULL UNIQUE,
    patient_id      INTEGER      NOT NULL,
    patient_name    VARCHAR(100) NOT NULL,
    bill_type       VARCHAR(20)  NOT NULL DEFAULT '门诊',
    visit_id        INTEGER,
    admission_id    INTEGER,
    total_amount    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    insurance_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    self_pay_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    paid_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    payment_method   VARCHAR(20)  NOT NULL DEFAULT '现金',
    insurance_type   VARCHAR(50)  DEFAULT '自费',
    status           VARCHAR(20)  NOT NULL DEFAULT '待缴费',
    operator_id      INTEGER,
    operator_name    VARCHAR(100),
    bill_date        DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 25. 收费明细表
-- ============================================================
CREATE TABLE IF NOT EXISTS billing_details (
    id              SERIAL PRIMARY KEY,
    bill_id         INTEGER      NOT NULL,
    item_type       VARCHAR(50)  NOT NULL,
    item_name       VARCHAR(200) NOT NULL,
    quantity        DECIMAL(10,2) NOT NULL DEFAULT 1,
    unit_price      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_price     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    insurance_ratio  DECIMAL(5,2) DEFAULT 0.00,
    notes           TEXT
);

-- ============================================================
-- 26. ICD-10 诊断编码表（精简版，后续可导入完整5000条）
-- ============================================================
CREATE TABLE IF NOT EXISTS icd10_codes (
    id          SERIAL PRIMARY KEY,
    code        VARCHAR(10)  NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    category    VARCHAR(100),
    description TEXT
);

-- ============================================================
-- 索引
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_patients_no         ON patients(patient_no);
CREATE INDEX IF NOT EXISTS idx_patients_name       ON patients(name);
CREATE INDEX IF NOT EXISTS idx_patients_phone      ON patients(phone);
CREATE INDEX IF NOT EXISTS idx_patients_idcard     ON patients(id_card);
CREATE INDEX IF NOT EXISTS idx_registrations_no    ON registrations(registration_no);
CREATE INDEX IF NOT EXISTS idx_registrations_date  ON registrations(visit_date);
CREATE INDEX IF NOT EXISTS idx_registrations_pid   ON registrations(patient_id);
CREATE INDEX IF NOT EXISTS idx_outpatient_pid      ON outpatient_visits(patient_id);
CREATE INDEX IF NOT EXISTS idx_outpatient_date     ON outpatient_visits(visit_date);
CREATE INDEX IF NOT EXISTS idx_inpatient_no        ON inpatient_records(admission_no);
CREATE INDEX IF NOT EXISTS idx_inpatient_pid       ON inpatient_records(patient_id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_vid   ON prescriptions(visit_id);
CREATE INDEX IF NOT EXISTS idx_billing_pid         ON billing_records(patient_id);
CREATE INDEX IF NOT EXISTS idx_billing_date        ON billing_records(bill_date);
CREATE INDEX IF NOT EXISTS idx_audit_created       ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_user          ON audit_logs(username);
CREATE INDEX IF NOT EXISTS idx_drug_inventory_did  ON drug_inventory(drug_id);
CREATE INDEX IF NOT EXISTS idx_exam_requests_pid   ON examination_requests(patient_id);

-- ============================================================
-- 初始数据
-- ============================================================

-- 科室数据
INSERT INTO departments (code, name, type, location) VALUES
('NKZJ', '内科综合', '门诊科室', '门诊楼1层'),
('WKZJ', '外科综合', '门诊科室', '门诊楼2层'),
('EKHK', '耳鼻喉科', '门诊科室', '门诊楼3层'),
('YKZJ', '眼科', '门诊科室', '门诊楼3层'),
('KQKJ', '口腔科', '门诊科室', '门诊楼4层'),
('FUK', '妇科', '门诊科室', '门诊楼5层'),
('CHANK', '产科', '门诊科室', '门诊楼5层'),
('ERKJ', '儿科', '门诊科室', '门诊楼6层'),
('XBK', '心内科', '专科门诊', '门诊楼7层'),
('HXKJ', '呼吸科', '专科门诊', '门诊楼7层'),
('XIAOHUA', '消化科', '专科门诊', '门诊楼8层'),
('SHENJ', '神经内科', '专科门诊', '门诊楼9层'),
('ZHENGU', '骨科', '专科门诊', '门诊楼10层'),
('PIFU', '皮肤科', '门诊科室', '门诊楼2层'),
('ZHONGYAO', '中医科', '门诊科室', '门诊楼1层'),
('JJZS', '急诊科', '急诊', '急诊楼1层'),
('JIANYAN', '检验科', '医技科室', '医技楼1层'),
('FANGYI', '放射科', '医技科室', '医技楼2层'),
('CHAOB', '超声科', '医技科室', '医技楼2层'),
('YAOFANG', '药剂科', '药剂科', '门诊楼负一层'),
('NK1', '内科住院', '住院科室', '住院楼3层'),
('WK1', '外科住院', '住院科室', '住院楼4层'),
('FK1', '妇产科住院', '住院科室', '住院楼5层'),
('EK1', '儿科住院', '住院科室', '住院楼6层'),
('ZZK', '重症医学科', 'ICU', '住院楼8层');

-- 医生样本数据
INSERT INTO doctors (code, name, gender, title, department_id, phone) VALUES
('D001', '张伟', '男', '主任医师', 1, '13800001001'),
('D002', '李静', '女', '副主任医师', 1, '13800001002'),
('D003', '王磊', '男', '主治医师', 1, '13800001003'),
('D004', '刘芳', '女', '住院医师', 1, '13800001004'),
('D005', '陈建国', '男', '主任医师', 2, '13800001005'),
('D006', '赵雪梅', '女', '副主任医师', 2, '13800001006'),
('D007', '黄志远', '男', '主治医师', 2, '13800001007'),
('D008', '吴晓燕', '女', '主任医师', 3, '13800001008'),
('D009', '郑国栋', '男', '副主任医师', 3, '13800001009'),
('D010', '孙玉兰', '女', '主任医师', 4, '13800001010'),
('D011', '周云飞', '男', '副主任医师', 4, '13800001011'),
('D012', '朱红梅', '女', '主任医师', 5, '13800001012'),
('D013', '徐明辉', '男', '主治医师', 5, '13800001013'),
('D014', '沈燕萍', '女', '主任医师', 6, '13800001014'),
('D015', '卢建军', '男', '副主任医师', 7, '13800001015'),
('D016', '何小丽', '女', '主任医师', 8, '13800001016'),
('D017', '冯国华', '男', '副主任医师', 8, '13800001017'),
('D018', '唐淑珍', '女', '主任医师', 9, '13800001018'),
('D019', '邓伟民', '男', '副主任医师', 9, '13800001019'),
('D020', '程洁', '女', '主任医师', 10, '13800001020'),
('D021', '傅大勇', '男', '副主任医师', 10, '13800001021'),
('D022', '谢利民', '男', '主任医师', 11, '13800001022'),
('D023', '蔡晓娜', '女', '主治医师', 11, '13800001023'),
('D024', '曾德贤', '男', '主任医师', 12, '13800001024'),
('D025', '龙小云', '女', '副主任医师', 12, '13800001025'),
('D026', '潘建华', '男', '主任医师', 13, '13800001026'),
('D027', '江秀珍', '女', '副主任医师', 13, '13800001027'),
('D028', '史志远', '男', '主治医师', 14, '13800001028'),
('D029', '魏小燕', '女', '主任医师', 15, '13800001029'),
('D030', '叶德明', '男', '主任医师', 16, '13800001030'),
('D031', '苗翠华', '女', '副主任医师', 16, '13800001031'),
('D032', '崔永强', '男', '主任医师', 17, '13800001032'),
('D033', '宋建英', '女', '主任医师', 18, '13800001033'),
('D034', '贺云龙', '男', '副主任医师', 18, '13800001034'),
('D035', '贾小敏', '女', '主任医师', 19, '13800001035'),
('D036', '范志刚', '男', '副主任医师', 21, '13800001036'),
('D037', '丁玉珍', '女', '主任医师', 21, '13800001037'),
('D038', '石国栋', '男', '主治医师', 22, '13800001038'),
('D039', '戴红梅', '女', '副主任医师', 22, '13800001039'),
('D040', '廖建国', '男', '主任医师', 23, '13800001040'),
('D041', '阮雪莲', '女', '主任医师', 24, '13800001041'),
('D042', '涂志远', '男', '副主任医师', 25, '13800001042'),
('D043', '欧阳峰', '男', '主任医师', 25, '13800001043'),
('D044', '司马红', '女', '主任医师', 16, '13800001044'),
('D045', '慕容龙', '男', '副主任医师', 4, '13800001045'),
('D046', '公孙玉', '女', '主治医师', 6, '13800001046'),
('D047', '东方明', '男', '主治医师', 2, '13800001047'),
('D048', '令狐月', '女', '住院医师', 8, '13800001048'),
('D049', '独孤建', '男', '住院医师', 9, '13800001049'),
('D050', '西门清', '男', '主任医师', 10, '13800001050');

-- 床位数据（示例：内科住院20张，外科住院20张）
INSERT INTO beds (bed_no, ward_name, department_id, bed_type, status) VALUES
('NK-001', '内科一病区', 21, '普通床', '空闲'),
('NK-002', '内科一病区', 21, '普通床', '空闲'),
('NK-003', '内科一病区', 21, '普通床', '空闲'),
('NK-004', '内科一病区', 21, '普通床', '空闲'),
('NK-005', '内科一病区', 21, '普通床', '空闲'),
('NK-006', '内科一病区', 21, '普通床', '空闲'),
('NK-007', '内科一病区', 21, '普通床', '空闲'),
('NK-008', '内科一病区', 21, '普通床', '空闲'),
('NK-009', '内科一病区', 21, '普通床', '空闲'),
('NK-010', '内科一病区', 21, '普通床', '空闲'),
('NK-VIP-001', '内科VIP病区', 21, 'VIP床', '空闲'),
('NK-VIP-002', '内科VIP病区', 21, 'VIP床', '空闲'),
('WK-001', '外科一病区', 22, '普通床', '空闲'),
('WK-002', '外科一病区', 22, '普通床', '空闲'),
('WK-003', '外科一病区', 22, '普通床', '空闲'),
('WK-004', '外科一病区', 22, '普通床', '空闲'),
('WK-005', '外科一病区', 22, '普通床', '空闲'),
('WK-006', '外科一病区', 22, '普通床', '空闲'),
('WK-007', '外科一病区', 22, '普通床', '空闲'),
('WK-008', '外科一病区', 22, '普通床', '空闲'),
('FK-001', '妇产科病区', 23, '普通床', '空闲'),
('FK-002', '妇产科病区', 23, '普通床', '空闲'),
('FK-003', '妇产科病区', 23, '普通床', '空闲'),
('FK-004', '妇产科病区', 23, '普通床', '空闲'),
('EK-001', '儿科病区', 24, '儿科床', '空闲'),
('EK-002', '儿科病区', 24, '儿科床', '空闲'),
('EK-003', '儿科病区', 24, '儿科床', '空闲'),
('ICU-001', 'ICU', 25, 'ICU床', '空闲'),
('ICU-002', 'ICU', 25, 'ICU床', '空闲'),
('ICU-003', 'ICU', 25, 'ICU床', '空闲');

-- 药品字典（常用药品示例）
INSERT INTO drugs (drug_code, generic_name, trade_name, drug_type, spec, unit, dosage_form) VALUES
('D-001', '阿莫西林胶囊', '阿莫仙', '西药', '0.25g', '粒', '胶囊'),
('D-002', '头孢克肟颗粒', '世福素', '西药', '50mg', '袋', '颗粒'),
('D-003', '布洛芬片', '芬必得', '西药', '0.4g', '片', '片剂'),
('D-004', '阿司匹林肠溶片', '拜阿司匹灵', '西药', '100mg', '片', '肠溶片'),
('D-005', '二甲双胍片', '格华止', '西药', '0.5g', '片', '片剂'),
('D-006', '苯磺酸氨氯地平片', '络活喜', '西药', '5mg', '片', '片剂'),
('D-007', '阿托伐他汀钙片', '立普妥', '西药', '20mg', '片', '片剂'),
('D-008', '奥美拉唑肠溶胶囊', '奥克', '西药', '20mg', '粒', '肠溶胶囊'),
('D-009', '盐酸二甲双胍缓释片', '卜可', '西药', '0.5g', '片', '缓释片'),
('D-010', '头孢呋辛酯片', '西力欣', '西药', '0.25g', '片', '片剂'),
('D-011', '氯化钠注射液', '生理盐水', '西药', '500ml', '袋', '注射液'),
('D-012', '葡萄糖注射液', '5%葡萄糖', '西药', '500ml', '袋', '注射液'),
('D-013', '维生素C片', '维生素C', '西药', '0.1g', '片', '片剂'),
('D-014', '板蓝根颗粒', '板蓝根', '中成药', '10g', '袋', '颗粒'),
('D-015', '藿香正气水', '藿香正气', '中成药', '10ml', '支', '口服液'),
('D-016', '连花清瘟胶囊', '连花清瘟', '中成药', '0.35g', '粒', '胶囊'),
('D-017', '逍遥丸', '逍遥丸', '中成药', '6g', '丸', '水蜜丸'),
('D-018', '硝苯地平片', '心痛定', '西药', '10mg', '片', '片剂'),
('D-019', '阿莫西林克拉维酸钾片', '奥格门汀', '西药', '0.375g', '片', '片剂'),
('D-020', '左氧氟沙星片', '可乐必妥', '西药', '0.5g', '片', '片剂');

-- 药品库存（对应药品，给初始库存）
INSERT INTO drug_inventory (drug_id, drug_name, batch_no, expiry_date, stock_qty, min_stock_qty, unit_price, retail_price) VALUES
(1, '阿莫西林胶囊', 'PC20240301', '2026-03-01', 5000, 500, 0.12, 0.35),
(2, '头孢克肟颗粒', 'PC20240401', '2026-04-01', 2000, 200, 2.50, 7.80),
(3, '布洛芬片', 'PC20240201', '2026-02-01', 3000, 300, 0.08, 0.25),
(4, '阿司匹林肠溶片', 'PC20240101', '2026-01-01', 10000, 1000, 0.05, 0.15),
(5, '二甲双胍片', 'PC20240501', '2026-05-01', 5000, 500, 0.10, 0.30),
(6, '苯磺酸氨氯地平片', 'PC20240601', '2026-06-01', 3000, 300, 0.50, 1.50),
(7, '阿托伐他汀钙片', 'PC20240301', '2026-03-01', 2000, 200, 1.20, 3.50),
(8, '奥美拉唑肠溶胶囊', 'PC20240401', '2026-04-01', 4000, 400, 0.80, 2.50),
(11, '氯化钠注射液', 'PC20240501', '2025-12-01', 1000, 100, 2.50, 5.00),
(12, '葡萄糖注射液', 'PC20240501', '2025-12-01', 800, 100, 2.80, 5.50),
(14, '板蓝根颗粒', 'PC20240201', '2026-02-01', 2000, 200, 0.50, 1.50),
(16, '连花清瘟胶囊', 'PC20240301', '2026-03-01', 3000, 300, 0.60, 1.80),
(20, '左氧氟沙星片', 'PC20240601', '2026-06-01', 1500, 150, 0.90, 2.80);

-- 检查项目
INSERT INTO exam_items (item_code, item_name, category, body_part, price) VALUES
('JY-001', '血常规', '检验', NULL, 25.00),
('JY-002', '尿常规', '检验', NULL, 15.00),
('JY-003', '大便常规', '检验', NULL, 15.00),
('JY-004', '血糖（空腹）', '检验', NULL, 8.00),
('JY-005', '血脂四项', '检验', NULL, 58.00),
('JY-006', '肝功能全套', '检验', NULL, 120.00),
('JY-007', '肾功能', '检验', NULL, 80.00),
('JY-008', '甲状腺功能三项', '检验', NULL, 180.00),
('JY-009', 'C反应蛋白', '检验', NULL, 20.00),
('JY-010', '凝血四项', '检验', NULL, 75.00),
('JY-011', '心肌酶谱', '检验', NULL, 95.00),
('JY-012', '乙肝两对半', '检验', NULL, 68.00),
('JY-013', '血型鉴定', '检验', NULL, 20.00),
('CT-001', 'CT平扫（头部）', 'CT', '头部', 280.00),
('CT-002', 'CT平扫（胸部）', 'CT', '胸部', 280.00),
('CT-003', 'CT平扫（腹部）', 'CT', '腹部', 280.00),
('CT-004', 'CT增强（胸部）', 'CT', '胸部', 580.00),
('MR-001', 'MRI（头部）', 'MRI', '头部', 650.00),
('MR-002', 'MRI（腰椎）', 'MRI', '腰椎', 650.00),
('CB-001', '腹部超声', '超声', '腹部', 120.00),
('CB-002', '心脏彩超', '超声', '心脏', 180.00),
('CB-003', '妇科超声（经腹）', '超声', '妇科', 100.00),
('XG-001', '胸部正侧位X线', 'X光', '胸部', 55.00),
('XG-002', '腰椎正侧位X线', 'X光', '腰椎', 55.00),
('ECG-001', '十二导联心电图', '心电图', NULL, 28.00);

-- 常用ICD-10诊断编码
INSERT INTO icd10_codes (code, name, category) VALUES
('J06.900', '上呼吸道感染', '呼吸系统'),
('J18.900', '肺炎', '呼吸系统'),
('J44.100', '慢性阻塞性肺疾病急性加重', '呼吸系统'),
('I10.X00', '高血压', '循环系统'),
('I25.100', '冠状动脉粥样硬化性心脏病', '循环系统'),
('I50.900', '心力衰竭', '循环系统'),
('E11.900', '2型糖尿病', '内分泌'),
('E78.500', '高脂血症', '内分泌'),
('K29.700', '胃炎', '消化系统'),
('K35.800', '急性阑尾炎', '消化系统'),
('K80.200', '胆囊结石', '消化系统'),
('N20.000', '肾结石', '泌尿系统'),
('N18.900', '慢性肾功能不全', '泌尿系统'),
('M54.500', '腰痛', '骨骼肌肉'),
('M48.000', '颈椎病', '骨骼肌肉'),
('S72.000', '股骨颈骨折', '损伤'),
('A09.900', '腹泻病', '传染病'),
('B19.900', '病毒性肝炎', '传染病'),
('G40.900', '癫痫', '神经系统'),
('G43.900', '偏头痛', '神经系统'),
('F41.900', '焦虑障碍', '精神疾患'),
('C34.900', '肺恶性肿瘤', '肿瘤'),
('C16.900', '胃恶性肿瘤', '肿瘤'),
('O80.X00', '正常分娩', '妊娠分娩'),
('P07.300', '早产儿', '围产期');

-- 系统配置初始数据
INSERT INTO system_configs (config_key, config_value, description, module) VALUES
('hospital.name', 'XX医院', '医院名称', 'system'),
('registration.fee.normal', '10.00', '普通挂号费（元）', 'registration'),
('registration.fee.senior', '30.00', '主任医师挂号费（元）', 'registration'),
('registration.fee.deputy', '20.00', '副主任医师挂号费（元）', 'registration'),
('insurance.employee.ratio', '0.85', '职工医保报销比例', 'billing'),
('insurance.resident.ratio', '0.70', '居民医保报销比例', 'billing'),
('insurance.newrural.ratio', '0.60', '新农合报销比例', 'billing'),
('pharmacy.low.stock.warning', 'true', '启用低库存预警', 'pharmacy'),
('inpatient.deposit.min', '3000.00', '住院最低押金（元）', 'inpatient'),
('inpatient.deposit.warn.ratio', '0.20', '押金余额低于此比例时预警', 'inpatient');

-- 默认系统用户（密码: admin123，BCrypt加密）
-- 幂等处理：先删除再插入，确保密码哈希是最新的
DELETE FROM system_users WHERE username IN ('admin','guahao','doctor','nurse','pharmacy','cashier');
INSERT INTO system_users (username, password_hash, display_name, role, is_active) VALUES
('admin', '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le', '系统管理员', '管理员', TRUE),
('guahao', '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le', '挂号示例用户', '挂号员', TRUE),
('doctor', '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le', '门诊医生示例', '门诊医生', TRUE),
('nurse', '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le', '护士示例', '住院医生', TRUE),
('pharmacy', '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le', '药剂师示例', '药剂师', TRUE),
('cashier', '$2a$10$iHnFMuakSKfWUXg4Lt7Vbu4L.JaLmf/pYRoQHSrUQw3f/IEexG2Le', '收费员示例', '收费员', TRUE);
