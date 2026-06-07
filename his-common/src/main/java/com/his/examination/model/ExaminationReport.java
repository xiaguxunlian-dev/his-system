package com.his.examination.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ExaminationReport {
    private int id;
    private int requestId;
    private String reportNo;
    private String examFindings;
    private String examConclusion;
    private String resultValues;
    private boolean isAbnormal;
    private boolean critical;
    private Integer examDoctorId;
    private String techName;
    private Integer reportDoctorId;
    private LocalDate examDate;
    private LocalDate reportDate;
    private String status;
    private String imagesPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 关联查询字段（非表字段）
    private String patientName;
    private String doctorName;

    public ExaminationReport() {
    }

    public ExaminationReport(int id, int requestId, String reportNo, String examFindings,
                              String examConclusion, String resultValues, boolean isAbnormal,
                              Integer examDoctorId, Integer reportDoctorId, LocalDate examDate,
                              LocalDate reportDate, String status, String imagesPath,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.requestId = requestId;
        this.reportNo = reportNo;
        this.examFindings = examFindings;
        this.examConclusion = examConclusion;
        this.resultValues = resultValues;
        this.isAbnormal = isAbnormal;
        this.examDoctorId = examDoctorId;
        this.reportDoctorId = reportDoctorId;
        this.examDate = examDate;
        this.reportDate = reportDate;
        this.status = status;
        this.imagesPath = imagesPath;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getReportNo() {
        return reportNo;
    }

    public void setReportNo(String reportNo) {
        this.reportNo = reportNo;
    }

    public String getExamFindings() {
        return examFindings;
    }

    public void setExamFindings(String examFindings) {
        this.examFindings = examFindings;
    }

    public String getExamConclusion() {
        return examConclusion;
    }

    public void setExamConclusion(String examConclusion) {
        this.examConclusion = examConclusion;
    }

    public String getResultValues() {
        return resultValues;
    }

    public void setResultValues(String resultValues) {
        this.resultValues = resultValues;
    }

    public boolean isAbnormal() {
        return isAbnormal;
    }

    public void setAbnormal(boolean abnormal) {
        isAbnormal = abnormal;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public Integer getExamDoctorId() {
        return examDoctorId;
    }

    public void setExamDoctorId(Integer examDoctorId) {
        this.examDoctorId = examDoctorId;
    }

    public String getTechName() {
        return techName;
    }

    public void setTechName(String techName) {
        this.techName = techName;
    }

    public Integer getReportDoctorId() {
        return reportDoctorId;
    }

    public void setReportDoctorId(Integer reportDoctorId) {
        this.reportDoctorId = reportDoctorId;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImagesPath() {
        return imagesPath;
    }

    public void setImagesPath(String imagesPath) {
        this.imagesPath = imagesPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // 关联查询字段 getter/setter
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
}
